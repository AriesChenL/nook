package com.lynn.nook.pay.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户订阅。一行对应一个 Stripe Subscription，状态由 customer.subscription.* 回调同步。
 */
@Data
@Table("subscriptions")
public class Subscription {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("stripe_customer_id")
    private String stripeCustomerId;

    @Column("stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column("price_id")
    private String priceId;

    @Column("product_code")
    private String productCode;

    /** Stripe 订阅状态：active / trialing / past_due / canceled / unpaid 等。 */
    private String status;

    /** 当前计费周期结束时间，到期前用于判定权益是否有效。 */
    @Column("current_period_end")
    private OffsetDateTime currentPeriodEnd;

    /** 是否在周期结束时取消（用户点了取消但仍享受到期）。 */
    @Column("cancel_at_period_end")
    private Boolean cancelAtPeriodEnd;

    @Column("latest_invoice_id")
    private String latestInvoiceId;

    @Column("trial_end")
    private OffsetDateTime trialEnd;

    @Column("canceled_at")
    private OffsetDateTime canceledAt;

    @Column("last_event_created")
    private Long lastEventCreated;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
