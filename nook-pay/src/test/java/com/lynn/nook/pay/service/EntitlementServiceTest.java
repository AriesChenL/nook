package com.lynn.nook.pay.service;

import com.lynn.nook.pay.dto.EntitlementVO;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 权益判定：以本地 Subscription 表为准，只有 active/trialing 且未过期算 pro。 */
class EntitlementServiceTest {

    private SubscriptionMapper subscriptionMapper;
    private EntitlementService service;

    @BeforeEach
    void setUp() {
        subscriptionMapper = mock(SubscriptionMapper.class);
        service = new EntitlementService(subscriptionMapper);
    }

    private Subscription sub(String status, OffsetDateTime periodEnd) {
        Subscription s = new Subscription();
        s.setStatus(status);
        s.setCurrentPeriodEnd(periodEnd);
        return s;
    }

    @Test
    void noSubscription_isFree() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        EntitlementVO e = service.forUser(7L);

        assertThat(e.plan()).isEqualTo("free");
        assertThat(e.active()).isFalse();
        assertThat(e.until()).isNull();
    }

    @Test
    void activeSubscription_isPro() {
        OffsetDateTime end = OffsetDateTime.now().plusDays(20);
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(sub("active", end));

        EntitlementVO e = service.forUser(7L);

        assertThat(e.plan()).isEqualTo("pro");
        assertThat(e.active()).isTrue();
        assertThat(e.until()).isEqualTo(end);
    }

    @Test
    void trialingSubscription_isPro() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(sub("trialing", OffsetDateTime.now().plusDays(7)));

        assertThat(service.forUser(7L).active()).isTrue();
    }

    @Test
    void canceledSubscription_isFree() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(sub("canceled", OffsetDateTime.now().plusDays(20)));

        assertThat(service.forUser(7L).plan()).isEqualTo("free");
    }

    @Test
    void pastDueSubscription_isFree() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(sub("past_due", OffsetDateTime.now().plusDays(20)));

        assertThat(service.forUser(7L).active()).isFalse();
    }

    @Test
    void activeButPeriodEnded_isFree() {
        when(subscriptionMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(sub("active", OffsetDateTime.now().minusHours(1)));

        assertThat(service.forUser(7L).plan()).isEqualTo("free");
    }

    @Test
    void nullUser_isFree() {
        assertThat(service.forUser(null).active()).isFalse();
        verifyNoInteractions(subscriptionMapper);
    }
}
