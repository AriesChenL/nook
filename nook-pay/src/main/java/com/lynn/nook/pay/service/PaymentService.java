package com.lynn.nook.pay.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.pay.config.StripeProperties;
import com.lynn.nook.pay.dto.CheckoutResponse;
import com.lynn.nook.pay.dto.CreateCheckoutRequest;
import com.lynn.nook.pay.dto.PortalResponse;
import com.lynn.nook.pay.dto.SubscriptionVO;
import com.lynn.nook.pay.dto.InvoiceVO;
import com.lynn.nook.pay.entity.PaymentCheckoutSession;
import com.lynn.nook.pay.entity.StripeCustomer;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.gateway.StripeGateway;
import com.lynn.nook.pay.mapper.PaymentCheckoutSessionMapper;
import com.lynn.nook.pay.mapper.PaymentInvoiceMapper;
import com.lynn.nook.pay.mapper.StripeCustomerMapper;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 订阅支付编排：Checkout、Customer、Billing Portal、回跳对账与账单查询。
 * 真正的「付款成功」由 {@link StripeWebhookService} 通过回调落地，本类只负责发起。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final StripeProperties props;
    private static final Set<String> BLOCKING_SUBSCRIPTION_STATUSES = Set.of(
            "active", "trialing", "incomplete", "past_due", "unpaid", "paused");

    private final StripeGateway stripe;
    private final BillingStateService billingStateService;
    private final PaymentCheckoutSessionMapper checkoutMapper;
    private final PaymentInvoiceMapper invoiceMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final StripeCustomerMapper customerMapper;
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    /** 订阅：确保用户有 Stripe Customer，再创建 subscription 模式的 Checkout 会话。 */
    @Transactional
    public CheckoutResponse createSubscriptionCheckout(Long userId, CreateCheckoutRequest req, String requestKey) {
        requireConfigured();
        String priceId = resolveSubscriptionPrice(req.productCode());
        String idempotencyKey = normalizeIdempotencyKey(requestKey);
        lockCheckoutForUser(userId);

        PaymentCheckoutSession existing = checkoutMapper.selectOneByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("idempotency_key = ?", idempotencyKey));
        if (existing != null) {
            if (!req.productCode().equals(existing.getProductCode())) {
                throw new BusinessException(ResultCode.PAY_IDEMPOTENCY_KEY_INVALID);
            }
            return toCheckoutResponse(existing);
        }
        PaymentCheckoutSession reusable = checkoutMapper.selectOneByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("product_code = ?", req.productCode())
                .and("status = 'open'")
                .and("expires_at > ?", OffsetDateTime.now())
                .orderBy("created_at desc")
                .limit(1));
        if (reusable != null) return toCheckoutResponse(reusable);

        rejectDuplicateSubscription(userId);
        String customerId = ensureCustomer(userId);

        try {
            Session session = stripe.createSubscriptionCheckout(customerId, userId, req.productCode(), priceId,
                    stripeCheckoutKey(userId, idempotencyKey));
            PaymentCheckoutSession row = saveCheckout(userId, idempotencyKey, req.productCode(), customerId, session);
            meterRegistry.counter("nook.pay.checkout.created", "product", req.productCode()).increment();
            return toCheckoutResponse(row);
        } catch (StripeException e) {
            log.error("创建订阅 Checkout 失败 userId={} product={} stripeRequestId={}",
                    userId, req.productCode(), e.getRequestId(), e);
            throw new BusinessException(ResultCode.PAY_STRIPE_ERROR);
        }
    }

    /** 打开 Billing Portal，让用户自助管理订阅（改套餐/取消/更新支付方式）。 */
    public PortalResponse createBillingPortal(Long userId) {
        requireConfigured();
        String customerId = ensureCustomer(userId);
        try {
            com.stripe.model.billingportal.Session session = stripe.createBillingPortal(
                    customerId, "nook-portal-" + UUID.randomUUID());
            return new PortalResponse(session.getUrl());
        } catch (StripeException e) {
            log.error("创建 Billing Portal 会话失败 userId={} stripeRequestId={}", userId, e.getRequestId(), e);
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

    /** Stripe 回跳后的主动对账。必须从 Stripe 重新读取，绝不把前端 success 参数当支付成功。 */
    public SubscriptionVO syncSubscription(Long userId, String sessionId) {
        requireConfigured();
        try {
            Session session = stripe.retrieveCheckoutSession(sessionId);
            verifyCheckoutOwner(userId, session);
            updateCheckout(session);
            if (session.getSubscription() != null && !session.getSubscription().isBlank()) {
                billingStateService.syncNow(stripe.retrieveSubscription(session.getSubscription()));
            }
            return getActiveSubscription(userId);
        } catch (StripeException e) {
            log.error("同步订阅失败 userId={} session={} stripeRequestId={}",
                    userId, sessionId, e.getRequestId(), e);
            throw new BusinessException(ResultCode.PAY_STRIPE_ERROR);
        }
    }

    public List<InvoiceVO> listInvoices(Long userId) {
        return invoiceMapper.selectListByQuery(QueryWrapper.create()
                        .where("user_id = ?", userId)
                        .orderBy("created_at desc")
                        .limit(50))
                .stream().map(InvoiceVO::from).toList();
    }

    /** 取出（或首次创建）用户对应的 Stripe Customer id，并落库映射。 */
    public String ensureCustomer(Long userId) {
        StripeCustomer existing = customerMapper.selectOneByQuery(
                QueryWrapper.create().where("user_id = ?", userId));
        if (existing != null) {
            return existing.getStripeCustomerId();
        }
        requireConfigured();
        try {
            String stableKey = UUID.nameUUIDFromBytes(
                    ("nook:stripe-customer:" + userId).getBytes(StandardCharsets.UTF_8)).toString();
            Customer customer = stripe.createCustomer(userId, "nook-customer-" + stableKey);
            StripeCustomer mapping = new StripeCustomer();
            mapping.setUserId(userId);
            mapping.setStripeCustomerId(customer.getId());
            mapping.setCreatedAt(OffsetDateTime.now());
            try {
                customerMapper.insert(mapping);
            } catch (DataIntegrityViolationException duplicate) {
                StripeCustomer concurrent = customerMapper.selectOneByQuery(
                        QueryWrapper.create().where("user_id = ?", userId));
                if (concurrent != null) return concurrent.getStripeCustomerId();
                throw duplicate;
            }
            return customer.getId();
        } catch (StripeException e) {
            log.error("创建 Stripe Customer 失败 userId={} stripeRequestId={}", userId, e.getRequestId(), e);
            throw new BusinessException(ResultCode.PAY_STRIPE_ERROR);
        }
    }

    private String resolveSubscriptionPrice(String productCode) {
        if (!props.isSubscriptionProduct(productCode)) {
            throw new BusinessException(ResultCode.PAY_PRODUCT_NOT_FOUND);
        }
        String priceId = props.priceId(productCode);
        if (priceId == null || priceId.isBlank()) {
            throw new BusinessException(ResultCode.PAY_PRODUCT_NOT_FOUND);
        }
        return priceId;
    }

    private void rejectDuplicateSubscription(Long userId) {
        Subscription sub = subscriptionMapper.selectOneByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .orderBy("updated_at desc")
                .limit(1));
        if (sub != null && BLOCKING_SUBSCRIPTION_STATUSES.contains(sub.getStatus())) {
            throw new BusinessException(ResultCode.PAY_SUBSCRIPTION_ALREADY_EXISTS);
        }
    }

    private PaymentCheckoutSession saveCheckout(Long userId, String idempotencyKey, String productCode,
                                                String customerId, Session session) {
        PaymentCheckoutSession row = new PaymentCheckoutSession();
        row.setUserId(userId);
        row.setIdempotencyKey(idempotencyKey);
        row.setProductCode(productCode);
        row.setStripeSessionId(session.getId());
        row.setStripeCustomerId(customerId);
        row.setStatus(session.getStatus() == null ? "open" : session.getStatus());
        row.setPaymentStatus(session.getPaymentStatus());
        row.setCheckoutUrl(session.getUrl());
        row.setExpiresAt(toOffset(session.getExpiresAt()));
        row.setCreatedAt(OffsetDateTime.now());
        row.setUpdatedAt(OffsetDateTime.now());
        try {
            checkoutMapper.insert(row);
            return row;
        } catch (DataIntegrityViolationException duplicate) {
            PaymentCheckoutSession concurrent = checkoutMapper.selectOneByQuery(QueryWrapper.create()
                    .where("user_id = ?", userId)
                    .and("idempotency_key = ?", idempotencyKey));
            if (concurrent != null) return concurrent;
            throw duplicate;
        }
    }

    private void verifyCheckoutOwner(Long userId, Session session) {
        if (session == null || !"subscription".equals(session.getMode())) {
            throw new BusinessException(ResultCode.PAY_CHECKOUT_NOT_FOUND);
        }
        PaymentCheckoutSession local = checkoutMapper.selectOneByQuery(QueryWrapper.create()
                .where("stripe_session_id = ?", session.getId()));
        if (local != null && !userId.equals(local.getUserId())) {
            throw new BusinessException(ResultCode.PAY_CHECKOUT_FORBIDDEN);
        }
        if (!String.valueOf(userId).equals(session.getClientReferenceId())) {
            throw new BusinessException(ResultCode.PAY_CHECKOUT_FORBIDDEN);
        }
    }

    private void updateCheckout(Session session) {
        PaymentCheckoutSession row = checkoutMapper.selectOneByQuery(QueryWrapper.create()
                .where("stripe_session_id = ?", session.getId()));
        if (row == null) return;
        row.setStatus(session.getStatus());
        row.setPaymentStatus(session.getPaymentStatus());
        row.setUpdatedAt(OffsetDateTime.now());
        checkoutMapper.update(row);
    }

    private static CheckoutResponse toCheckoutResponse(PaymentCheckoutSession row) {
        return new CheckoutResponse(row.getCheckoutUrl(), row.getStripeSessionId());
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        String key = value.trim();
        if (key.length() < 8 || key.length() > 180 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(ResultCode.PAY_IDEMPOTENCY_KEY_INVALID);
        }
        return key;
    }

    private static String stripeCheckoutKey(Long userId, String requestKey) {
        String userScope = UUID.nameUUIDFromBytes(
                ("nook:stripe-user:" + userId).getBytes(StandardCharsets.UTF_8)).toString();
        return "nook-subscription-checkout-" + userScope + "-" + requestKey;
    }

    private void lockCheckoutForUser(Long userId) {
        // PostgreSQL transaction advisory lock: same user cannot create two Checkout sessions concurrently,
        // while different users remain fully parallel. The lock is released automatically on commit/rollback.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, userId);
                statement.execute();
            }
            return null;
        });
    }

    private void requireConfigured() {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.PAY_NOT_CONFIGURED);
        }
    }

    private static OffsetDateTime toOffset(Long epochSeconds) {
        return epochSeconds == null ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
