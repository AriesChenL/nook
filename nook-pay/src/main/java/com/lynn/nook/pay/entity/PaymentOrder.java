package com.lynn.nook.pay.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 一次性付款订单。本地记录支付意图，最终状态以 Stripe Webhook 为准。
 */
@Data
@Table("payment_orders")
public class PaymentOrder {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 对外暴露的订单号（UUID），写进 Checkout Session metadata 便于回调时定位。 */
    @Column("public_id")
    private String publicId;

    @Column("user_id")
    private Long userId;

    /** 商品/套餐编码（对应 StripeProperties.prices 的 key）。 */
    @Column("product_code")
    private String productCode;

    private Integer quantity;

    /** 金额（最小货币单位，如分），下单时未知可空，回调写回。 */
    @Column("amount_total")
    private Long amountTotal;

    private String currency;

    @Column("stripe_session_id")
    private String stripeSessionId;

    @Column("stripe_payment_intent_id")
    private String stripePaymentIntentId;

    /** CREATED / PAID / EXPIRED / FAILED */
    private String status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("paid_at")
    private OffsetDateTime paidAt;
}
