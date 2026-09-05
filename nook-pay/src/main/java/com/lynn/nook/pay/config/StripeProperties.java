package com.lynn.nook.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

/**
 * Stripe 接入配置。绑定前缀 {@code nook.stripe}，建议把密钥放 Nacos 配置中心而非代码里。
 */
@Data
@ConfigurationProperties(prefix = "nook.stripe")
public class StripeProperties {

    /** Stripe 受限密钥（推荐 rk_ 开头）或私钥（sk_）。用于所有服务端 API 调用。 */
    private String apiKey;

    /** Webhook 签名密钥（whsec_ 开头），用于校验回调来源真实性。 */
    private String webhookSecret;

    /** 支付成功跳转地址。{CHECKOUT_SESSION_ID} 会被 Stripe 替换成真实会话 id。 */
    private String successUrl;

    /** 用户取消支付跳转地址。 */
    private String cancelUrl;

    /** Billing Portal（订阅自助管理页）返回地址。 */
    private String portalReturnUrl;

    /** Stripe API 网络重试与超时。只重试 SDK 判定为安全的临时网络故障。 */
    private int maxNetworkRetries = 2;
    private int connectTimeoutMillis = 10_000;
    private int readTimeoutMillis = 30_000;

    /** Webhook 签名时间容差，默认 5 分钟。 */
    private long webhookToleranceSeconds = 300;

    /** Checkout 能力开关。支付方式不在代码中硬编码，由 Stripe Dashboard 动态决定。 */
    private boolean allowPromotionCodes = true;
    private boolean automaticTaxEnabled = false;

    /**
     * 订阅套餐编码 -> Stripe Price id 的映射。
     * 前端只传编码，服务端在此解析成 price_xxx，避免客户端伪造价格。
     */
    private Map<String, String> prices = Map.of();

    /** 明确允许创建订阅且能授予权益的产品编码。一次性商品不得放入此集合。 */
    private Set<String> subscriptionProducts = Set.of("pro_monthly");

    public String priceId(String code) {
        return prices.get(code);
    }

    public boolean isSubscriptionProduct(String code) {
        return code != null && subscriptionProducts.contains(code);
    }

    public String productCode(String priceId) {
        if (priceId == null) return null;
        return prices.entrySet().stream()
                .filter(entry -> isSubscriptionProduct(entry.getKey()))
                .filter(entry -> priceId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
