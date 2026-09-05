package com.lynn.nook.pay.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("payment_invoices")
public class PaymentInvoice {

    @Id(keyType = KeyType.Auto)
    private Long id;
    @Column("user_id")
    private Long userId;
    @Column("stripe_invoice_id")
    private String stripeInvoiceId;
    @Column("stripe_subscription_id")
    private String stripeSubscriptionId;
    @Column("invoice_number")
    private String invoiceNumber;
    private String status;
    @Column("billing_reason")
    private String billingReason;
    private String currency;
    @Column("amount_due")
    private Long amountDue;
    @Column("amount_paid")
    private Long amountPaid;
    @Column("hosted_invoice_url")
    private String hostedInvoiceUrl;
    @Column("invoice_pdf")
    private String invoicePdf;
    @Column("period_start")
    private OffsetDateTime periodStart;
    @Column("period_end")
    private OffsetDateTime periodEnd;
    @Column("paid_at")
    private OffsetDateTime paidAt;
    @Column("last_event_type")
    private String lastEventType;
    @Column("last_event_created")
    private Long lastEventCreated;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
