package com.lynn.nook.pay.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe 配置健康检查。SDK 客户端由 StripeSdkGateway 按配置构造，避免使用全局静态 apiKey。
 */
@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties props;

    @Bean("stripe")
    public HealthIndicator stripeHealthIndicator() {
        return () -> {
            boolean apiConfigured = hasText(props.getApiKey());
            boolean webhookConfigured = hasText(props.getWebhookSecret());
            Health.Builder builder = apiConfigured && webhookConfigured
                    ? Health.up()
                    : Health.outOfService();
            return builder
                    .withDetail("apiKeyConfigured", apiConfigured)
                    .withDetail("webhookSecretConfigured", webhookConfigured)
                    .withDetail("subscriptionProducts", props.getSubscriptionProducts().size())
                    .build();
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
