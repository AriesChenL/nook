package com.lynn.nook.pay.gateway;

import com.lynn.nook.pay.config.StripeProperties;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StripeSdkGateway implements StripeGateway {

    private final StripeProperties props;

    @Override
    public Event constructWebhookEvent(String payload, String signature) throws SignatureVerificationException {
        return Webhook.constructEvent(
                payload, signature, props.getWebhookSecret(), props.getWebhookToleranceSeconds());
    }

    @Override
    public Customer createCustomer(Long userId, String idempotencyKey) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .putMetadata("userId", String.valueOf(userId))
                .build();
        return client().v1().customers().create(params, options(idempotencyKey));
    }

    @Override
    public com.stripe.model.checkout.Session createSubscriptionCheckout(
            String customerId, Long userId, String productCode, String priceId, String idempotencyKey)
            throws StripeException {
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setSuccessUrl(props.getSuccessUrl())
                .setCancelUrl(props.getCancelUrl())
                .setClientReferenceId(String.valueOf(userId))
                .setAllowPromotionCodes(props.isAllowPromotionCodes())
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("productCode", productCode)
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("userId", String.valueOf(userId))
                        .putMetadata("productCode", productCode)
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build());

        if (props.isAutomaticTaxEnabled()) {
            builder.setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build())
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    .setCustomerUpdate(SessionCreateParams.CustomerUpdate.builder()
                            .setAddress(SessionCreateParams.CustomerUpdate.Address.AUTO)
                            .setName(SessionCreateParams.CustomerUpdate.Name.AUTO)
                            .build());
        }

        // 不设置 payment_method_types：由 Stripe Dashboard 动态选择可用支付方式。
        return client().v1().checkout().sessions().create(builder.build(), options(idempotencyKey));
    }

    @Override
    public com.stripe.model.checkout.Session retrieveCheckoutSession(String sessionId) throws StripeException {
        return client().v1().checkout().sessions().retrieve(sessionId);
    }

    @Override
    public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
        return client().v1().subscriptions().retrieve(subscriptionId);
    }

    @Override
    public Invoice retrieveInvoice(String invoiceId) throws StripeException {
        return client().v1().invoices().retrieve(invoiceId);
    }

    @Override
    public com.stripe.model.billingportal.Session createBillingPortal(String customerId, String idempotencyKey)
            throws StripeException {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(props.getPortalReturnUrl())
                        .build();
        return client().v1().billingPortal().sessions().create(params, options(idempotencyKey));
    }

    private StripeClient client() {
        return StripeClient.builder()
                .setApiKey(props.getApiKey())
                .setMaxNetworkRetries(props.getMaxNetworkRetries())
                .setConnectTimeout(props.getConnectTimeoutMillis())
                .setReadTimeout(props.getReadTimeoutMillis())
                .build();
    }

    private static RequestOptions options(String idempotencyKey) {
        return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
    }
}
