package com.lynn.nook.pay.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.pay.config.StripeProperties;
import com.lynn.nook.pay.dto.CheckoutResponse;
import com.lynn.nook.pay.dto.CreateCheckoutRequest;
import com.lynn.nook.pay.entity.PaymentCheckoutSession;
import com.lynn.nook.pay.entity.StripeCustomer;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.gateway.StripeGateway;
import com.lynn.nook.pay.mapper.PaymentCheckoutSessionMapper;
import com.lynn.nook.pay.mapper.PaymentInvoiceMapper;
import com.lynn.nook.pay.mapper.StripeCustomerMapper;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private StripeGateway stripe;
    private BillingStateService billingState;
    private PaymentCheckoutSessionMapper checkoutMapper;
    private PaymentInvoiceMapper invoiceMapper;
    private SubscriptionMapper subscriptionMapper;
    private StripeCustomerMapper customerMapper;
    private JdbcTemplate jdbcTemplate;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        StripeProperties props = new StripeProperties();
        props.setApiKey("rk_test");
        props.setPrices(Map.of("pro_monthly", "price_pro"));
        stripe = mock(StripeGateway.class);
        billingState = mock(BillingStateService.class);
        checkoutMapper = mock(PaymentCheckoutSessionMapper.class);
        invoiceMapper = mock(PaymentInvoiceMapper.class);
        subscriptionMapper = mock(SubscriptionMapper.class);
        customerMapper = mock(StripeCustomerMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new PaymentService(props, stripe, billingState, checkoutMapper, invoiceMapper,
                subscriptionMapper, customerMapper, new SimpleMeterRegistry(), jdbcTemplate);
    }

    @Test
    void createsIdempotentSubscriptionCheckout() throws Exception {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(customer(7L, "cus_1"));
        Session session = checkout("cs_1", "7", "sub_1");
        session.setUrl("https://checkout.stripe.test/cs_1");
        session.setStatus("open");
        when(stripe.createSubscriptionCheckout(eq("cus_1"), eq(7L), eq("pro_monthly"),
                eq("price_pro"), startsWith("nook-subscription-checkout-"))).thenReturn(session);

        CheckoutResponse result = service.createSubscriptionCheckout(
                7L, new CreateCheckoutRequest("pro_monthly"), "request-123");

        assertThat(result.sessionId()).isEqualTo("cs_1");
        verify(checkoutMapper).insert(any(PaymentCheckoutSession.class));
    }

    @Test
    void sameIdempotencyKeyReturnsStoredCheckout() {
        PaymentCheckoutSession stored = new PaymentCheckoutSession();
        stored.setProductCode("pro_monthly");
        stored.setStripeSessionId("cs_existing");
        stored.setCheckoutUrl("https://checkout/existing");
        when(checkoutMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(stored);

        CheckoutResponse result = service.createSubscriptionCheckout(
                7L, new CreateCheckoutRequest("pro_monthly"), "request-123");

        assertThat(result.sessionId()).isEqualTo("cs_existing");
        verifyNoInteractions(stripe);
    }

    @Test
    void secondClickReusesOpenCheckoutEvenWithAnotherRequestKey() {
        PaymentCheckoutSession reusable = new PaymentCheckoutSession();
        reusable.setProductCode("pro_monthly");
        reusable.setStripeSessionId("cs_open");
        reusable.setCheckoutUrl("https://checkout/open");
        when(checkoutMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(reusable);

        CheckoutResponse result = service.createSubscriptionCheckout(
                7L, new CreateCheckoutRequest("pro_monthly"), "request-456");

        assertThat(result.sessionId()).isEqualTo("cs_open");
        verifyNoInteractions(stripe);
    }

    @Test
    void rejectsUnsupportedProductAndInvalidIdempotencyKey() {
        assertThatThrownBy(() -> service.createSubscriptionCheckout(
                7L, new CreateCheckoutRequest("credits_100"), "request-123"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_PRODUCT_NOT_FOUND.getCode());
        assertThatThrownBy(() -> service.createSubscriptionCheckout(
                7L, new CreateCheckoutRequest("pro_monthly"), "bad key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_IDEMPOTENCY_KEY_INVALID.getCode());
    }

    @Test
    void rejectsSecondSubscriptionWhenExistingOneNeedsPortalManagement() {
        Subscription current = new Subscription();
        current.setStatus("past_due");
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(current);

        assertThatThrownBy(() -> service.createSubscriptionCheckout(
                7L, new CreateCheckoutRequest("pro_monthly"), "request-123"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_SUBSCRIPTION_ALREADY_EXISTS.getCode());
        verifyNoInteractions(stripe);
    }

    @Test
    void syncCheckoutVerifiesOwnershipAndRefreshesSubscription() throws Exception {
        Session session = checkout("cs_1", "7", "sub_1");
        when(stripe.retrieveCheckoutSession("cs_1")).thenReturn(session);
        com.stripe.model.Subscription remote = new com.stripe.model.Subscription();
        when(stripe.retrieveSubscription("sub_1")).thenReturn(remote);
        Subscription local = new Subscription();
        local.setStatus("active");
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(local);

        service.syncSubscription(7L, "cs_1");

        verify(billingState).syncNow(remote);
    }

    @Test
    void syncCheckoutRejectsAnotherUsersSession() throws Exception {
        when(stripe.retrieveCheckoutSession("cs_1")).thenReturn(checkout("cs_1", "8", "sub_1"));
        assertThatThrownBy(() -> service.syncSubscription(7L, "cs_1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_CHECKOUT_FORBIDDEN.getCode());
        verify(stripe, never()).retrieveSubscription(anyString());
    }

    @Test
    void ensureCustomerCreatesAndPersistsMapping() throws Exception {
        Customer remote = new Customer();
        remote.setId("cus_new");
        when(stripe.createCustomer(eq(7L), startsWith("nook-customer-"))).thenReturn(remote);

        assertThat(service.ensureCustomer(7L)).isEqualTo("cus_new");
        verify(customerMapper).insert(argThat(row -> row.getUserId().equals(7L)
                && row.getStripeCustomerId().equals("cus_new")));
    }

    private Session checkout(String id, String userId, String subscriptionId) {
        Session session = new Session();
        session.setId(id);
        session.setMode("subscription");
        session.setClientReferenceId(userId);
        session.setSubscription(subscriptionId);
        session.setStatus("complete");
        session.setPaymentStatus("paid");
        return session;
    }

    private StripeCustomer customer(Long userId, String id) {
        StripeCustomer customer = new StripeCustomer();
        customer.setUserId(userId);
        customer.setStripeCustomerId(id);
        return customer;
    }
}
