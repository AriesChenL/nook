package com.lynn.nook.pay.service;

import com.lynn.nook.pay.config.StripeProperties;
import com.lynn.nook.pay.dto.EntitlementVO;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EntitlementServiceTest {

    private SubscriptionMapper subscriptionMapper;
    private EntitlementService service;

    @BeforeEach
    void setUp() {
        subscriptionMapper = mock(SubscriptionMapper.class);
        StripeProperties props = new StripeProperties();
        props.setPrices(Map.of("pro_monthly", "price_pro"));
        service = new EntitlementService(subscriptionMapper, props);
    }

    private Subscription sub(String status, OffsetDateTime periodEnd) {
        Subscription s = new Subscription();
        s.setStatus(status);
        s.setCurrentPeriodEnd(periodEnd);
        s.setProductCode("pro_monthly");
        s.setPriceId("price_pro");
        return s;
    }

    @Test
    void noSubscriptionIsFree() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        assertThat(service.forUser(7L).active()).isFalse();
    }

    @Test
    void activeConfiguredSubscriptionIsPro() {
        OffsetDateTime end = OffsetDateTime.now().plusDays(20);
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(sub("active", end));
        EntitlementVO result = service.forUser(7L);
        assertThat(result.plan()).isEqualTo("pro");
        assertThat(result.until()).isEqualTo(end);
    }

    @Test
    void trialingConfiguredSubscriptionIsPro() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(sub("trialing", OffsetDateTime.now().plusDays(7)));
        assertThat(service.forUser(7L).active()).isTrue();
    }

    @Test
    void unknownPriceNeverGrantsEntitlement() {
        Subscription sub = sub("active", OffsetDateTime.now().plusDays(20));
        sub.setPriceId("price_unknown");
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(sub);
        assertThat(service.forUser(7L).active()).isFalse();
    }

    @Test
    void inactiveOrExpiredSubscriptionIsFree() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(sub("past_due", OffsetDateTime.now().plusDays(20)))
                .thenReturn(sub("active", OffsetDateTime.now().minusHours(1)));
        assertThat(service.forUser(7L).active()).isFalse();
        assertThat(service.forUser(7L).active()).isFalse();
    }

    @Test
    void missingPeriodEndNeverGrantsIndefiniteEntitlement() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(sub("active", null));
        assertThat(service.forUser(7L).active()).isFalse();
    }

    @Test
    void nullUserIsFreeWithoutDatabaseCall() {
        assertThat(service.forUser(null).active()).isFalse();
        verifyNoInteractions(subscriptionMapper);
    }
}
