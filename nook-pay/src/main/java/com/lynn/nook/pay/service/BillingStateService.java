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
import com.stripe.model.SubscriptionItem;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 把 Stripe 的订阅与账单快照幂等同步到本地，所有入口共用同一套乱序保护。 */
@Service
@RequiredArgsConstructor
public class BillingStateService {

    private final StripeProperties props;
    private final SubscriptionMapper subscriptionMapper;
    private final PaymentInvoiceMapper invoiceMapper;
    private final StripeCustomerMapper customerMapper;

    public Subscription syncSubscription(com.stripe.model.Subscription source, long eventCreated) {
        if (source == null || source.getId() == null || source.getCustomer() == null) {
            throw new IllegalArgumentException("Stripe subscription is missing id or customer");
        }

        Long userId = resolveUserId(source.getCustomer(), source.getMetadata());
        if (userId == null) {
            throw new IllegalStateException("Stripe customer is not mapped to a Nook user: " + source.getCustomer());
        }

        Subscription row = subscriptionMapper.selectOneByQuery(QueryWrapper.create()
                .where("stripe_subscription_id = ?", source.getId()));
        boolean isNew = row == null;
        if (isNew) {
            row = new Subscription();
            row.setUserId(userId);
            row.setStripeCustomerId(source.getCustomer());
            row.setStripeSubscriptionId(source.getId());
            row.setCreatedAt(OffsetDateTime.now());
            row.setLastEventCreated(0L);
        }
        if (row.getLastEventCreated() != null && row.getLastEventCreated() > eventCreated) {
            return row;
        }

        row.setStatus(source.getStatus());
        row.setCancelAtPeriodEnd(Boolean.TRUE.equals(source.getCancelAtPeriodEnd()));
        row.setLatestInvoiceId(source.getLatestInvoice());
        row.setTrialEnd(toOffset(source.getTrialEnd()));
        row.setCanceledAt(toOffset(source.getCanceledAt()));

        List<SubscriptionItem> items = source.getItems() == null ? null : source.getItems().getData();
        if (items != null && !items.isEmpty()) {
            SubscriptionItem item = items.getFirst();
            if (item.getPrice() != null) {
                row.setPriceId(item.getPrice().getId());
                row.setProductCode(props.productCode(item.getPrice().getId()));
            }
            row.setCurrentPeriodEnd(toOffset(item.getCurrentPeriodEnd()));
        }
        row.setLastEventCreated(eventCreated);
        row.setUpdatedAt(OffsetDateTime.now());

        if (isNew) subscriptionMapper.insert(row);
        else subscriptionMapper.update(row);
        return row;
    }

    public PaymentInvoice syncInvoice(Invoice source, String eventType, long eventCreated) {
        if (source == null || source.getId() == null || source.getCustomer() == null) {
            throw new IllegalArgumentException("Stripe invoice is missing id or customer");
        }
        Long userId = resolveUserId(source.getCustomer(), source.getMetadata());
        if (userId == null) {
            throw new IllegalStateException("Stripe customer is not mapped to a Nook user: " + source.getCustomer());
        }

        PaymentInvoice row = invoiceMapper.selectOneByQuery(QueryWrapper.create()
                .where("stripe_invoice_id = ?", source.getId()));
        boolean isNew = row == null;
        if (isNew) {
            row = new PaymentInvoice();
            row.setUserId(userId);
            row.setStripeInvoiceId(source.getId());
            row.setCreatedAt(OffsetDateTime.now());
            row.setLastEventCreated(0L);
        }
        if (row.getLastEventCreated() != null && row.getLastEventCreated() > eventCreated) {
            return row;
        }

        row.setStripeSubscriptionId(subscriptionId(source));
        row.setInvoiceNumber(source.getNumber());
        row.setStatus(source.getStatus() == null ? "unknown" : source.getStatus());
        row.setBillingReason(source.getBillingReason());
        row.setCurrency(source.getCurrency());
        row.setAmountDue(source.getAmountDue());
        row.setAmountPaid(source.getAmountPaid());
        row.setHostedInvoiceUrl(source.getHostedInvoiceUrl());
        row.setInvoicePdf(source.getInvoicePdf());
        row.setPeriodStart(toOffset(source.getPeriodStart()));
        row.setPeriodEnd(toOffset(source.getPeriodEnd()));
        row.setPaidAt(source.getStatusTransitions() == null
                ? null : toOffset(source.getStatusTransitions().getPaidAt()));
        row.setLastEventType(eventType);
        row.setLastEventCreated(eventCreated);
        row.setUpdatedAt(OffsetDateTime.now());

        if (isNew) invoiceMapper.insert(row);
        else invoiceMapper.update(row);
        return row;
    }

    public Subscription syncNow(com.stripe.model.Subscription source) {
        return syncSubscription(source, Instant.now().getEpochSecond());
    }

    private Long resolveUserId(String customerId, Map<String, String> metadata) {
        StripeCustomer mapping = customerMapper.selectOneByQuery(QueryWrapper.create()
                .where("stripe_customer_id = ?", customerId));
        if (mapping != null) return mapping.getUserId();

        Long metadataUserId = parsePositiveLong(metadata == null ? null : metadata.get("userId"));
        if (metadataUserId == null) return null;

        StripeCustomer recovered = new StripeCustomer();
        recovered.setUserId(metadataUserId);
        recovered.setStripeCustomerId(customerId);
        recovered.setCreatedAt(OffsetDateTime.now());
        try {
            customerMapper.insert(recovered);
        } catch (DataIntegrityViolationException ignored) {
            StripeCustomer concurrent = customerMapper.selectOneByQuery(QueryWrapper.create()
                    .where("stripe_customer_id = ?", customerId));
            if (concurrent != null) return concurrent.getUserId();
            throw ignored;
        }
        return metadataUserId;
    }

    private static String subscriptionId(Invoice invoice) {
        Invoice.Parent parent = invoice.getParent();
        if (parent == null || parent.getSubscriptionDetails() == null) return null;
        return parent.getSubscriptionDetails().getSubscription();
    }

    private static Long parsePositiveLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static OffsetDateTime toOffset(Long epochSeconds) {
        return epochSeconds == null ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
