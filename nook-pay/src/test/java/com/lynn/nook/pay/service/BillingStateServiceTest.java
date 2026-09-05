package com.lynn.nook.pay.service;

import com.lynn.nook.pay.config.StripeProperties;
import com.lynn.nook.pay.entity.PaymentInvoice;
import com.lynn.nook.pay.entity.StripeCustomer;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.mapper.PaymentInvoiceMapper;
import com.lynn.nook.pay.mapper.StripeCustomerMapper;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.stripe.model.Invoice;
import com.stripe.model.Price;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingStateServiceTest {

    private SubscriptionMapper subscriptionMapper;
    private PaymentInvoiceMapper invoiceMapper;
    private StripeCustomerMapper customerMapper;
    private BillingStateService service;

    @BeforeEach
    void setUp() {
        StripeProperties props = new StripeProperties();
        props.setPrices(Map.of("pro_monthly", "price_pro"));
        subscriptionMapper = mock(SubscriptionMapper.class);
        invoiceMapper = mock(PaymentInvoiceMapper.class);
        customerMapper = mock(StripeCustomerMapper.class);
        service = new BillingStateService(props, subscriptionMapper, invoiceMapper, customerMapper);
    }

    @Test
    void insertsSubscriptionAndMapsConfiguredProduct() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(customer(7L, "cus_1"));
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        service.syncSubscription(subscription("active", Map.of()), 200L);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getProductCode()).isEqualTo("pro_monthly");
        assertThat(captor.getValue().getLastEventCreated()).isEqualTo(200L);
    }

    @Test
    void ignoresOlderSubscriptionEvent() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(customer(7L, "cus_1"));
        Subscription existing = new Subscription();
        existing.setLastEventCreated(300L);
        existing.setStatus("active");
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existing);

        Subscription result = service.syncSubscription(subscription("canceled", Map.of()), 200L);

        assertThat(result.getStatus()).isEqualTo("active");
        verify(subscriptionMapper, never()).update(any());
    }

    @Test
    void recoversMissingCustomerMappingFromSignedMetadata() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        service.syncSubscription(subscription("active", Map.of("userId", "9")), 200L);

        ArgumentCaptor<StripeCustomer> customer = ArgumentCaptor.forClass(StripeCustomer.class);
        verify(customerMapper).insert(customer.capture());
        assertThat(customer.getValue().getUserId()).isEqualTo(9L);
    }

    @Test
    void rejectsUnknownCustomerWithoutMetadata() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.syncSubscription(subscription("active", Map.of()), 200L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not mapped");
        verifyNoInteractions(subscriptionMapper);
    }

    @Test
    void synchronizesInvoiceHistory() {
        when(customerMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(customer(7L, "cus_1"));
        when(invoiceMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        Invoice invoice = new Invoice();
        invoice.setId("in_1");
        invoice.setCustomer("cus_1");
        invoice.setStatus("paid");
        invoice.setAmountDue(999L);
        invoice.setAmountPaid(999L);
        invoice.setCurrency("usd");
        Invoice.Parent.SubscriptionDetails details = new Invoice.Parent.SubscriptionDetails();
        details.setSubscription("sub_1");
        Invoice.Parent parent = new Invoice.Parent();
        parent.setType("subscription_details");
        parent.setSubscriptionDetails(details);
        invoice.setParent(parent);

        service.syncInvoice(invoice, "invoice.paid", 400L);

        ArgumentCaptor<PaymentInvoice> captor = ArgumentCaptor.forClass(PaymentInvoice.class);
        verify(invoiceMapper).insert(captor.capture());
        assertThat(captor.getValue().getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(captor.getValue().getStatus()).isEqualTo("paid");
        assertThat(captor.getValue().getAmountPaid()).isEqualTo(999L);
    }

    private com.stripe.model.Subscription subscription(String status, Map<String, String> metadata) {
        com.stripe.model.Subscription source = new com.stripe.model.Subscription();
        source.setId("sub_1");
        source.setCustomer("cus_1");
        source.setStatus(status);
        source.setMetadata(metadata);
        source.setCancelAtPeriodEnd(false);
        Price price = new Price();
        price.setId("price_pro");
        SubscriptionItem item = new SubscriptionItem();
        item.setPrice(price);
        item.setCurrentPeriodEnd(1_900_000_000L);
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));
        source.setItems(items);
        return source;
    }

    private StripeCustomer customer(Long userId, String customerId) {
        StripeCustomer customer = new StripeCustomer();
        customer.setUserId(userId);
        customer.setStripeCustomerId(customerId);
        return customer;
    }
}
