package com.lynn.nook.pay.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("payment_checkout_sessions")
public class PaymentCheckoutSession {

    @Id(keyType = KeyType.Auto)
    private Long id;
    @Column("user_id")
    private Long userId;
    @Column("idempotency_key")
    private String idempotencyKey;
    @Column("product_code")
    private String productCode;
    @Column("stripe_session_id")
    private String stripeSessionId;
    @Column("stripe_customer_id")
    private String stripeCustomerId;
    private String status;
    @Column("payment_status")
    private String paymentStatus;
    @Column("checkout_url")
    private String checkoutUrl;
    @Column("expires_at")
    private OffsetDateTime expiresAt;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
