package com.lynn.nook.pay.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.pay.config.StripeProperties;
import com.lynn.nook.pay.dto.CheckoutResponse;
import com.lynn.nook.pay.dto.CreateCheckoutRequest;
import com.lynn.nook.pay.dto.PortalResponse;
import com.lynn.nook.pay.dto.SubscriptionVO;
import com.lynn.nook.pay.entity.PaymentOrder;
import com.lynn.nook.pay.entity.StripeCustomer;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.mapper.PaymentOrderMapper;
import com.lynn.nook.pay.mapper.StripeCustomerMapper;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 支付编排：创建一次性付款 / 订阅的 Checkout 会话、维护 Stripe Customer、打开 Billing Portal。
 * 真正的「付款成功」由 {@link StripeWebhookService} 通过回调落地，本类只负责发起。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final StripeProperties props;
    private final PaymentOrderMapper orderMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final StripeCustomerMapper customerMapper;

    /** 一次性付款：创建 payment 模式的 Checkout 会话，并落一条 CREATED 订单。 */
    public CheckoutResponse createOneTimeCheckout(Long userId, CreateCheckoutRequest req) {
        String priceId = resolvePrice(req.productCode());
        int qty = Math.max(1, req.quantity());

        String orderPublicId = UUID.randomUUID().toString();
        PaymentOrder order = new PaymentOrder();
        order.setPublicId(orderPublicId);
        order.setUserId(userId);
        order.setProductCode(req.productCode());
        order.setQuantity(qty);
        order.setStatus("CREATED");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        orderMapper.insert(order);

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(props.getSuccessUrl())
                    .setCancelUrl(props.getCancelUrl())
                    .setClientReferenceId(String.valueOf(userId))
                    .putMetadata("orderPublicId", orderPublicId)
                    .putMetadata("userId", String.valueOf(userId))
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity((long) qty)
                            .build())
                    .build();
            Session session = Session.create(params);

            order.setStripeSessionId(session.getId());
            order.setUpdatedAt(OffsetDateTime.now());
            orderMapper.update(order);

            return new CheckoutResponse(session.getUrl(), session.getId(), orderPublicId);
        } catch (StripeException e) {
            log.error("创建一次性支付会话失败 userId={} product={}", userId, req.productCode(), e);
            throw new BusinessException(ResultCode.PAY_STRIPE_ERROR);
        }
    }

    /** 订阅：确保用户有 Stripe Customer，再创建 subscription 模式的 Checkout 会话。 */
    public CheckoutResponse createSubscriptionCheckout(Long userId, CreateCheckoutRequest req) {
        String priceId = resolvePrice(req.productCode());
        String customerId = ensureCustomer(userId);

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(props.getSuccessUrl())
                    .setCancelUrl(props.getCancelUrl())
                    .setClientReferenceId(String.valueOf(userId))
                    .putMetadata("userId", String.valueOf(userId))
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .build();
            Session session = Session.create(params);
            return new CheckoutResponse(session.getUrl(), session.getId(), null);
        } catch (StripeException e) {
            log.error("创建订阅支付会话失败 userId={} product={}", userId, req.productCode(), e);
            throw new BusinessException(ResultCode.PAY_STRIPE_ERROR);
        }
    }

    /** 打开 Billing Portal，让用户自助管理订阅（改套餐/取消/更新支付方式）。 */
    public PortalResponse createBillingPortal(Long userId) {
        String customerId = ensureCustomer(userId);
        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .setReturnUrl(props.getPortalReturnUrl())
                            .build();
            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params);
            return new PortalResponse(session.getUrl());
        } catch (StripeException e) {
            log.error("创建 Billing Portal 会话失败 userId={}", userId, e);
            throw new BusinessException(ResultCode.PAY_STRIPE_ERROR);
        }
    }

    /** 查询用户当前订阅（取最近一条）。无订阅返回 null。 */
    public SubscriptionVO getActiveSubscription(Long userId) {
        Subscription sub = subscriptionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("user_id = ?", userId)
                        .orderBy("updated_at desc")
                        .limit(1));
        return sub == null ? null : SubscriptionVO.from(sub);
    }

    /** 取出（或首次创建）用户对应的 Stripe Customer id，并落库映射。 */
    public String ensureCustomer(Long userId) {
        StripeCustomer existing = customerMapper.selectOneByQuery(
                QueryWrapper.create().where("user_id = ?", userId));
        if (existing != null) {
            return existing.getStripeCustomerId();
        }
        try {
            Customer customer = Customer.create(CustomerCreateParams.builder()
                    .putMetadata("userId", String.valueOf(userId))
                    .build());
            StripeCustomer mapping = new StripeCustomer();
            mapping.setUserId(userId);
            mapping.setStripeCustomerId(customer.getId());
            mapping.setCreatedAt(OffsetDateTime.now());
            customerMapper.insert(mapping);
            return customer.getId();
        } catch (StripeException e) {
            log.error("创建 Stripe Customer 失败 userId={}", userId, e);
            throw new BusinessException(ResultCode.PAY_STRIPE_ERROR);
        }
    }

    private String resolvePrice(String productCode) {
        String priceId = props.priceId(productCode);
        if (priceId == null || priceId.isBlank()) {
            throw new BusinessException(ResultCode.PAY_PRODUCT_NOT_FOUND);
        }
        return priceId;
    }
}
