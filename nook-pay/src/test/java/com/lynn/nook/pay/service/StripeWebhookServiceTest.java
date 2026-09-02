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
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Price;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stripe Webhook 处理：验签失败映射、幂等去重、一次性付款与订阅事件落地。
 * 验签本身（HMAC）用 mockStatic 隔离——只验证本类对结果的处理，不重测 Stripe SDK 的加密。
 */
class StripeWebhookServiceTest {

    private static final String SECRET = "whsec_test_123";
    private static final String PAYLOAD = "{\"id\":\"evt_1\"}";
    private static final String SIG = "t=1,v1=deadbeef";

    private StripeProperties props;
    private StripeEventMapper eventMapper;
    private PaymentOrderMapper orderMapper;
    private SubscriptionMapper subscriptionMapper;
    private StripeCustomerMapper customerMapper;
    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        props = new StripeProperties();
        props.setWebhookSecret(SECRET);
        eventMapper = mock(StripeEventMapper.class);
        orderMapper = mock(PaymentOrderMapper.class);
        subscriptionMapper = mock(SubscriptionMapper.class);
        customerMapper = mock(StripeCustomerMapper.class);
        service = new StripeWebhookService(props, eventMapper, orderMapper, subscriptionMapper, customerMapper);
        // 默认：事件未处理过
        lenient().when(eventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
    }

    private Event mockEvent(String id, String type, Object dataObject) {
        Event event = mock(Event.class);
        lenient().when(event.getId()).thenReturn(id);
        lenient().when(event.getType()).thenReturn(type);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        lenient().when(deser.getObject()).thenReturn(Optional.ofNullable((com.stripe.model.StripeObject) dataObject));
        lenient().when(event.getDataObjectDeserializer()).thenReturn(deser);
        return event;
    }

    private MockedStatic<Webhook> stubConstruct(Event event) {
        MockedStatic<Webhook> mocked = mockStatic(Webhook.class);
        mocked.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);
        return mocked;
    }

    // ---------- 验签 / 配置 ----------

    @Test
    void rejectsWhenWebhookSecretMissing() {
        props.setWebhookSecret("  ");
        assertThatThrownBy(() -> service.handle(PAYLOAD, SIG))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_NOT_CONFIGURED.getCode());
        verifyNoInteractions(eventMapper, orderMapper, subscriptionMapper);
    }

    @Test
    void mapsSignatureFailureToBusinessError() {
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new SignatureVerificationException("bad sig", SIG));

            assertThatThrownBy(() -> service.handle(PAYLOAD, SIG))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID.getCode());
        }
        verify(eventMapper, never()).insert(any());
    }

    // ---------- 幂等 ----------

    @Test
    void skipsAlreadyProcessedEvent() {
        when(eventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        Event event = mockEvent("evt_dup", "checkout.session.completed", null);

        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        verify(eventMapper, never()).insert(any());
        verifyNoInteractions(orderMapper, subscriptionMapper);
    }

    @Test
    void recordsUnhandledEventTypeButDoesNothingElse() {
        Event event = mockEvent("evt_ping", "ping", null);

        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        ArgumentCaptor<StripeEvent> cap = ArgumentCaptor.forClass(StripeEvent.class);
        verify(eventMapper).insert(cap.capture());
        assertThat(cap.getValue().getEventId()).isEqualTo("evt_ping");
        assertThat(cap.getValue().getType()).isEqualTo("ping");
        verifyNoInteractions(orderMapper, subscriptionMapper);
    }

    // ---------- 一次性付款 ----------

    @Test
    void checkoutCompleted_marksOrderPaid() {
        Session session = new Session();
        session.setId("cs_1");
        session.setMode("payment");
        session.setMetadata(Map.of("orderPublicId", "ord_1"));
        session.setPaymentIntent("pi_1");
        session.setAmountTotal(990L);
        session.setCurrency("usd");

        PaymentOrder order = new PaymentOrder();
        order.setPublicId("ord_1");
        order.setUserId(42L);
        order.setStatus("PENDING");
        when(orderMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(order);

        Event event = mockEvent("evt_co", "checkout.session.completed", session);
        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        ArgumentCaptor<PaymentOrder> cap = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orderMapper).update(cap.capture());
        PaymentOrder saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("PAID");
        assertThat(saved.getStripePaymentIntentId()).isEqualTo("pi_1");
        assertThat(saved.getAmountTotal()).isEqualTo(990L);
        assertThat(saved.getPaidAt()).isNotNull();
        verify(eventMapper).insert(any());
    }

    @Test
    void checkoutCompleted_ignoresNonPaymentMode() {
        Session session = new Session();
        session.setMode("subscription"); // 订阅由 customer.subscription.* 负责

        Event event = mockEvent("evt_co2", "checkout.session.completed", session);
        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        verify(orderMapper, never()).update(any());
        verify(eventMapper).insert(any()); // 仍记录已处理
    }

    @Test
    void checkoutCompleted_idempotentWhenOrderAlreadyPaid() {
        Session session = new Session();
        session.setMode("payment");
        session.setMetadata(Map.of("orderPublicId", "ord_1"));

        PaymentOrder order = new PaymentOrder();
        order.setStatus("PAID");
        when(orderMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(order);

        Event event = mockEvent("evt_co3", "checkout.session.completed", session);
        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        verify(orderMapper, never()).update(any());
    }

    // ---------- 订阅 ----------

    private com.stripe.model.Subscription stripeSub(String id, String customer, String status,
                                                   String priceId, Long periodEnd) {
        com.stripe.model.Subscription sub = new com.stripe.model.Subscription();
        sub.setId(id);
        sub.setCustomer(customer);
        sub.setStatus(status);
        sub.setCancelAtPeriodEnd(false);

        Price price = new Price();
        price.setId(priceId);
        SubscriptionItem item = new SubscriptionItem();
        item.setPrice(price);
        item.setCurrentPeriodEnd(periodEnd);
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));
        sub.setItems(items);
        return sub;
    }

    @Test
    void subscriptionChange_insertsNewSubscription() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(customerMapping(7L, "cus_1"));
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Event event = mockEvent("evt_su", "customer.subscription.updated",
                stripeSub("sub_1", "cus_1", "active", "price_pro", 1893456000L));
        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionMapper).insert(cap.capture());
        Subscription saved = cap.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getPriceId()).isEqualTo("price_pro");
        assertThat(saved.getCurrentPeriodEnd()).isNotNull();
        verify(subscriptionMapper, never()).update(any());
    }

    @Test
    void subscriptionChange_updatesExistingSubscription() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(customerMapping(7L, "cus_1"));
        Subscription existing = new Subscription();
        existing.setId(100L);
        existing.setUserId(7L);
        existing.setStripeSubscriptionId("sub_1");
        existing.setStatus("active");
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existing);

        Event event = mockEvent("evt_sd", "customer.subscription.deleted",
                stripeSub("sub_1", "cus_1", "canceled", "price_pro", 1893456000L));
        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionMapper).update(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("canceled");
        verify(subscriptionMapper, never()).insert(any());
    }

    @Test
    void subscriptionChange_skipsWhenCustomerUnknown() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        Event event = mockEvent("evt_su2", "customer.subscription.updated",
                stripeSub("sub_x", "cus_unknown", "active", "price_pro", 1893456000L));
        try (MockedStatic<Webhook> mocked = stubConstruct(event)) {
            service.handle(PAYLOAD, SIG);
        }

        verify(subscriptionMapper, never()).insert(any());
        verify(subscriptionMapper, never()).update(any());
        verify(eventMapper).insert(any()); // 仍记录已处理，避免 Stripe 无限重投
    }

    private StripeCustomer customerMapping(Long userId, String customerId) {
        StripeCustomer c = new StripeCustomer();
        c.setUserId(userId);
        c.setStripeCustomerId(customerId);
        return c;
    }
}
