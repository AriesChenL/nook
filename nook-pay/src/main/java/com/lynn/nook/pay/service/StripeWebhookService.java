package com.lynn.nook.pay.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.pay.config.StripeProperties;
import com.lynn.nook.pay.entity.PaymentOrder;
import com.lynn.nook.pay.entity.StripeCustomer;
import com.lynn.nook.pay.entity.StripeEvent;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.mapper.PaymentOrderMapper;
import com.lynn.nook.pay.mapper.StripeCustomerMapper;
import com.lynn.nook.pay.mapper.StripeEventMapper;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.SubscriptionItem;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Stripe Webhook 处理：验签 -> 幂等去重 -> 按事件类型落地。
 * 「付款成功 / 订阅状态」以这里为唯一可信来源，前端跳转成功页不代表真付款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeProperties props;
    private final StripeEventMapper eventMapper;
    private final PaymentOrderMapper orderMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final StripeCustomerMapper customerMapper;

    /**
     * 入口：校验签名并分发处理。验签失败抛业务异常（控制器转 4xx）。
     * 业务处理本身的异常向上抛出，让控制器返回非 2xx，Stripe 会自动重试。
     */
    @Transactional
    public void handle(String payload, String signature) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            throw new BusinessException(ResultCode.PAY_NOT_CONFIGURED);
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, props.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe Webhook 验签失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID);
        }

        // 幂等：Stripe 会重试投递，已处理过的 event 直接跳过
        if (alreadyProcessed(event.getId())) {
            log.debug("重复事件已忽略 event={} type={}", event.getId(), event.getType());
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> handleSubscriptionChange(event);
            default -> log.debug("未处理的事件类型 type={}", event.getType());
        }

        recordProcessed(event);
    }

    // ---- 一次性付款 ----

    private void handleCheckoutCompleted(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof com.stripe.model.checkout.Session session)) {
            log.warn("checkout.session.completed 反序列化失败 event={}", event.getId());
            return;
        }
        // 订阅模式的会话由 customer.subscription.* 负责落地，这里只处理一次性付款
        if (!"payment".equals(session.getMode())) {
            return;
        }
        String orderPublicId = session.getMetadata() == null ? null
                : session.getMetadata().get("orderPublicId");
        if (orderPublicId == null) {
            log.warn("一次性付款会话缺少 orderPublicId session={}", session.getId());
            return;
        }
        PaymentOrder order = orderMapper.selectOneByQuery(
                QueryWrapper.create().where("public_id = ?", orderPublicId));
        if (order == null) {
            log.warn("找不到本地订单 orderPublicId={}", orderPublicId);
            return;
        }
        if ("PAID".equals(order.getStatus())) {
            return; // 已处理
        }
        order.setStatus("PAID");
        order.setStripePaymentIntentId(session.getPaymentIntent());
        order.setAmountTotal(session.getAmountTotal());
        order.setCurrency(session.getCurrency());
        order.setPaidAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        orderMapper.update(order);

        log.info("订单已支付 orderPublicId={} userId={} amount={} {}",
                orderPublicId, order.getUserId(), order.getAmountTotal(), order.getCurrency());

        // TODO: 在此发放权益（充值点数 / 解锁功能）。建议改为发领域事件，由对应服务消费，
        //       保持 nook-pay 只负责「记录支付事实」，不耦合具体业务。
    }

    // ---- 订阅 ----

    private void handleSubscriptionChange(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof com.stripe.model.Subscription sub)) {
            log.warn("subscription 事件反序列化失败 event={}", event.getId());
            return;
        }
        Long userId = userIdByCustomer(sub.getCustomer());
        if (userId == null) {
            log.warn("订阅找不到对应用户 customer={}", sub.getCustomer());
            return;
        }

        Subscription row = subscriptionMapper.selectOneByQuery(
                QueryWrapper.create().where("stripe_subscription_id = ?", sub.getId()));
        boolean isNew = row == null;
        if (isNew) {
            row = new Subscription();
            row.setUserId(userId);
            row.setStripeCustomerId(sub.getCustomer());
            row.setStripeSubscriptionId(sub.getId());
            row.setCreatedAt(OffsetDateTime.now());
        }
        row.setStatus(sub.getStatus());
        row.setCancelAtPeriodEnd(sub.getCancelAtPeriodEnd());

        List<SubscriptionItem> items = sub.getItems() == null ? null : sub.getItems().getData();
        if (items != null && !items.isEmpty()) {
            SubscriptionItem item = items.get(0);
            if (item.getPrice() != null) {
                row.setPriceId(item.getPrice().getId());
            }
            // 计费周期结束时间在新版 API 中位于 subscription item 上
            if (item.getCurrentPeriodEnd() != null) {
                row.setCurrentPeriodEnd(toOffset(item.getCurrentPeriodEnd()));
            }
        }
        row.setUpdatedAt(OffsetDateTime.now());

        if (isNew) {
            subscriptionMapper.insert(row);
        } else {
            subscriptionMapper.update(row);
        }
        log.info("订阅已同步 userId={} sub={} status={} cancelAtEnd={}",
                userId, sub.getId(), sub.getStatus(), sub.getCancelAtPeriodEnd());

        // TODO: 按 status/到期时间发放或回收会员权益。
    }

    // ---- 工具 ----

    private Long userIdByCustomer(String customerId) {
        if (customerId == null) return null;
        StripeCustomer mapping = customerMapper.selectOneByQuery(
                QueryWrapper.create().where("stripe_customer_id = ?", customerId));
        return mapping == null ? null : mapping.getUserId();
    }

    private boolean alreadyProcessed(String eventId) {
        return eventMapper.selectCountByQuery(
                QueryWrapper.create().where("event_id = ?", eventId)) > 0;
    }

    private void recordProcessed(Event event) {
        StripeEvent rec = new StripeEvent();
        rec.setEventId(event.getId());
        rec.setType(event.getType());
        rec.setReceivedAt(OffsetDateTime.now());
        eventMapper.insert(rec);
    }

    private static OffsetDateTime toOffset(Long epochSeconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
