package com.lynn.nook.pay.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.pay.config.StripeProperties;
import com.lynn.nook.pay.entity.PaymentCheckoutSession;
import com.lynn.nook.pay.gateway.StripeGateway;
import com.lynn.nook.pay.mapper.PaymentCheckoutSessionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.HasId;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Stripe Webhook：验签、事务内原子去重、乱序安全的订阅/账单快照同步。
 * 任何无法安全处理的已知事件都抛出异常，让 Stripe 重试，而不是静默丢失支付事实。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeProperties props;
    private final StripeGateway stripe;
    private final WebhookEventStore eventStore;
    private final BillingStateService billingStateService;
    private final PaymentCheckoutSessionMapper checkoutMapper;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void handle(String payload, String signature) {
        requireWebhookConfigured(signature);

        Event event;
        try {
            event = stripe.constructWebhookEvent(payload, signature);
        } catch (SignatureVerificationException e) {
            meterRegistry.counter("nook.pay.webhook.events", "type", "signature", "outcome", "rejected")
                    .increment();
            log.warn("Stripe Webhook 验签失败");
            throw new BusinessException(ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID);
        }

        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new BusinessException(ResultCode.PAY_WEBHOOK_EVENT_INVALID));
        String objectId = object instanceof HasId hasId ? hasId.getId() : null;
        if (!eventStore.claim(event, objectId)) {
            meterRegistry.counter("nook.pay.webhook.events", "type", event.getType(), "outcome", "duplicate")
                    .increment();
            return;
        }

        try {
            dispatch(event, object);
            meterRegistry.counter("nook.pay.webhook.events", "type", event.getType(), "outcome", "processed")
                    .increment();
        } catch (RuntimeException e) {
            meterRegistry.counter("nook.pay.webhook.events", "type", event.getType(), "outcome", "failed")
                    .increment();
            throw e;
        }
    }

    private void dispatch(Event event, StripeObject object) {
        long created = event.getCreated() == null ? 0L : event.getCreated();
        switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.expired",
                 "checkout.session.async_payment_failed" -> handleCheckout(event, requireType(object, Session.class), created);
            case "customer.subscription.created", "customer.subscription.updated",
                 "customer.subscription.deleted", "customer.subscription.paused",
                 "customer.subscription.resumed" -> syncAuthoritativeSubscription(
                    event, requireType(object, com.stripe.model.Subscription.class), created);
            case "invoice.created", "invoice.finalized", "invoice.finalization_failed",
                 "invoice.paid", "invoice.payment_failed", "invoice.payment_action_required",
                 "invoice.voided", "invoice.marked_uncollectible" -> syncAuthoritativeInvoice(
                    event, requireType(object, Invoice.class), created);
            default -> log.debug("忽略未订阅的 Stripe 事件 type={} event={}", event.getType(), event.getId());
        }
    }

    private void syncAuthoritativeSubscription(Event event, com.stripe.model.Subscription snapshot, long created) {
        try {
            billingStateService.syncSubscription(stripe.retrieveSubscription(snapshot.getId()), created);
        } catch (StripeException e) {
            throw stripeReadFailure(event, "subscription", snapshot.getId(), e);
        }
    }

    private void syncAuthoritativeInvoice(Event event, Invoice snapshot, long created) {
        try {
            billingStateService.syncInvoice(stripe.retrieveInvoice(snapshot.getId()), event.getType(), created);
        } catch (StripeException e) {
            throw stripeReadFailure(event, "invoice", snapshot.getId(), e);
        }
    }

    private void handleCheckout(Event event, Session session, long created) {
        PaymentCheckoutSession local = checkoutMapper.selectOneByQuery(QueryWrapper.create()
                .where("stripe_session_id = ?", session.getId()));
        if (local != null) {
            local.setStatus(session.getStatus());
            local.setPaymentStatus(session.getPaymentStatus());
            local.setUpdatedAt(OffsetDateTime.now());
            checkoutMapper.update(local);
        }

        if ("checkout.session.completed".equals(event.getType())
                && "subscription".equals(session.getMode())
                && session.getSubscription() != null) {
            try {
                // 重新读取权威对象，避免 Checkout 与 subscription 事件到达顺序影响本地状态。
                billingStateService.syncSubscription(stripe.retrieveSubscription(session.getSubscription()), created);
            } catch (StripeException e) {
                throw stripeReadFailure(event, "subscription", session.getSubscription(), e);
            }
        }
    }

    private IllegalStateException stripeReadFailure(Event event, String resource, String resourceId,
                                                    StripeException error) {
        log.error("Webhook 拉取 Stripe 对象失败 event={} resource={} resourceId={} stripeRequestId={}",
                event.getId(), resource, resourceId, error.getRequestId(), error);
        return new IllegalStateException("Failed to retrieve Stripe " + resource, error);
    }

    private void requireWebhookConfigured(String signature) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            throw new BusinessException(ResultCode.PAY_NOT_CONFIGURED);
        }
        if (signature == null || signature.isBlank()) {
            throw new BusinessException(ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID);
        }
    }

    private static <T> T requireType(StripeObject object, Class<T> expected) {
        if (!expected.isInstance(object)) {
            throw new BusinessException(ResultCode.PAY_WEBHOOK_EVENT_INVALID);
        }
        return expected.cast(object);
    }
}
