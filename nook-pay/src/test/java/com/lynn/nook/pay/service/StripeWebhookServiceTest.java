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
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StripeWebhookServiceTest {

    private StripeProperties props;
    private StripeGateway stripe;
    private WebhookEventStore eventStore;
    private BillingStateService billingState;
    private PaymentCheckoutSessionMapper checkoutMapper;
    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        props = new StripeProperties();
        props.setWebhookSecret("whsec_test");
        stripe = mock(StripeGateway.class);
        eventStore = mock(WebhookEventStore.class);
        billingState = mock(BillingStateService.class);
        checkoutMapper = mock(PaymentCheckoutSessionMapper.class);
        service = new StripeWebhookService(props, stripe, eventStore, billingState,
                checkoutMapper, new SimpleMeterRegistry());
        lenient().when(eventStore.claim(any(), any())).thenReturn(true);
    }

    @Test
    void rejectsMissingWebhookSecret() {
        props.setWebhookSecret(" ");
        assertThatThrownBy(() -> service.handle("{}", "sig"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_NOT_CONFIGURED.getCode());
        verifyNoInteractions(stripe);
    }

    @Test
    void rejectsMissingSignature() {
        assertThatThrownBy(() -> service.handle("{}", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID.getCode());
    }

    @Test
    void mapsSignatureFailure() throws Exception {
        when(stripe.constructWebhookEvent(anyString(), anyString()))
                .thenThrow(new SignatureVerificationException("bad", "sig"));
        assertThatThrownBy(() -> service.handle("{}", "sig"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID.getCode());
    }

    @Test
    void duplicateEventDoesNotRunBusinessLogic() throws Exception {
        com.stripe.model.Subscription subscription = new com.stripe.model.Subscription();
        subscription.setId("sub_1");
        when(stripe.retrieveSubscription("sub_1")).thenReturn(subscription);
        Event event = event("evt_1", "customer.subscription.updated", subscription);
        when(stripe.constructWebhookEvent(anyString(), anyString())).thenReturn(event);
        when(eventStore.claim(event, "sub_1")).thenReturn(false);

        service.handle("{}", "sig");

        verifyNoInteractions(billingState);
    }

    @Test
    void invalidEventObjectIsRetryableFailure() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deser);
        when(stripe.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

        assertThatThrownBy(() -> service.handle("{}", "sig"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PAY_WEBHOOK_EVENT_INVALID.getCode());
        verifyNoInteractions(eventStore);
    }

    @Test
    void subscriptionEventSynchronizesState() throws Exception {
        com.stripe.model.Subscription subscription = new com.stripe.model.Subscription();
        subscription.setId("sub_1");
        when(stripe.retrieveSubscription("sub_1")).thenReturn(subscription);
        Event event = event("evt_1", "customer.subscription.updated", subscription);
        when(stripe.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

        service.handle("{}", "sig");

        verify(billingState).syncSubscription(subscription, 100L);
    }

    @Test
    void invoiceEventSynchronizesInvoice() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setId("in_1");
        when(stripe.retrieveInvoice("in_1")).thenReturn(invoice);
        Event event = event("evt_2", "invoice.payment_failed", invoice);
        when(stripe.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

        service.handle("{}", "sig");

        verify(billingState).syncInvoice(invoice, "invoice.payment_failed", 100L);
    }

    @Test
    void completedCheckoutUpdatesLocalStateAndReadsAuthoritativeSubscription() throws Exception {
        Session session = new Session();
        session.setId("cs_1");
        session.setMode("subscription");
        session.setStatus("complete");
        session.setPaymentStatus("paid");
        session.setSubscription("sub_1");
        PaymentCheckoutSession local = new PaymentCheckoutSession();
        when(checkoutMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(local);
        com.stripe.model.Subscription subscription = new com.stripe.model.Subscription();
        when(stripe.retrieveSubscription("sub_1")).thenReturn(subscription);
        Event event = event("evt_3", "checkout.session.completed", session);
        when(stripe.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

        service.handle("{}", "sig");

        verify(checkoutMapper).update(local);
        verify(billingState).syncSubscription(subscription, 100L);
    }

    @Test
    void stripeReadFailurePropagatesSoWebhookWillRetry() throws Exception {
        Session session = new Session();
        session.setId("cs_1");
        session.setMode("subscription");
        session.setSubscription("sub_1");
        Event event = event("evt_3", "checkout.session.completed", session);
        when(stripe.constructWebhookEvent(anyString(), anyString())).thenReturn(event);
        when(stripe.retrieveSubscription("sub_1")).thenThrow(mock(StripeException.class));

        assertThatThrownBy(() -> service.handle("{}", "sig"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Event event(String id, String type, StripeObject object) {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(object));
        when(event.getDataObjectDeserializer()).thenReturn(deser);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        when(event.getCreated()).thenReturn(100L);
        return event;
    }
}
