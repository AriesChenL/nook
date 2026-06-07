package com.lynn.nook.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

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

    /**
     * 商品/套餐编码 -> Stripe Price id 的映射。
     * 前端只传编码，服务端在此解析成 price_xxx，避免客户端伪造价格。
     * 一次性商品与订阅套餐都放这里，由调用的接口决定用 payment 还是 subscription 模式。
     */
    private Map<String, String> prices = Map.of();

    public String priceId(String code) {
        return prices.get(code);
    }
}
