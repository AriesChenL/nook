package com.lynn.nook.pay.dto;

import com.lynn.nook.pay.entity.Subscription;

import java.time.OffsetDateTime;

/**
 * 订阅对外视图。供前端展示「当前套餐 / 到期时间 / 是否已取消」。
 */
public record SubscriptionVO(String productCode,
                             String priceId,
                             String status,
                             OffsetDateTime currentPeriodEnd,
                             Boolean cancelAtPeriodEnd,
                             OffsetDateTime trialEnd,
                             OffsetDateTime canceledAt) {

    public static SubscriptionVO from(Subscription s) {
        return new SubscriptionVO(s.getProductCode(), s.getPriceId(), s.getStatus(),
                s.getCurrentPeriodEnd(), s.getCancelAtPeriodEnd(), s.getTrialEnd(), s.getCanceledAt());
    }
}
