package com.lynn.nook.pay.gateway;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;

public interface StripeGateway {

    Event constructWebhookEvent(String payload, String signature) throws SignatureVerificationException;

    Customer createCustomer(Long userId, String idempotencyKey) throws StripeException;

    com.stripe.model.checkout.Session createSubscriptionCheckout(
            String customerId, Long userId, String productCode, String priceId, String idempotencyKey)
            throws StripeException;

    com.stripe.model.checkout.Session retrieveCheckoutSession(String sessionId) throws StripeException;

    Subscription retrieveSubscription(String subscriptionId) throws StripeException;

    Invoice retrieveInvoice(String invoiceId) throws StripeException;

    Session createBillingPortal(String customerId, String idempotencyKey) throws StripeException;
}
