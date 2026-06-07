package com.lynn.nook.pay.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 平台用户 -> Stripe Customer 的映射。一个用户一个 Customer，订阅与 Billing Portal 都基于它。
 */
@Data
@Table("stripe_customers")
public class StripeCustomer {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("stripe_customer_id")
    private String stripeCustomerId;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
