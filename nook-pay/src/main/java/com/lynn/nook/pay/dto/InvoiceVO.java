package com.lynn.nook.pay.dto;

import com.lynn.nook.pay.entity.PaymentInvoice;

import java.time.OffsetDateTime;

public record InvoiceVO(String stripeInvoiceId,
                        String invoiceNumber,
                        String status,
                        String currency,
                        Long amountDue,
                        Long amountPaid,
                        String hostedInvoiceUrl,
                        String invoicePdf,
                        OffsetDateTime periodStart,
                        OffsetDateTime periodEnd,
                        OffsetDateTime paidAt,
                        String lastEventType) {

    public static InvoiceVO from(PaymentInvoice invoice) {
        return new InvoiceVO(invoice.getStripeInvoiceId(), invoice.getInvoiceNumber(),
                invoice.getStatus(), invoice.getCurrency(),
                invoice.getAmountDue(), invoice.getAmountPaid(), invoice.getHostedInvoiceUrl(),
                invoice.getInvoicePdf(), invoice.getPeriodStart(), invoice.getPeriodEnd(), invoice.getPaidAt(),
                invoice.getLastEventType());
    }
}
