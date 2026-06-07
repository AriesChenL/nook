package com.lynn.nook.pay.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 把配置里的密钥注入 Stripe 全局客户端。单账户场景用全局 apiKey 即可。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties props;

    @PostConstruct
    public void init() {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("nook.stripe.api-key 未配置，Stripe 相关接口将不可用");
            return;
        }
        Stripe.apiKey = props.getApiKey();
        log.info("Stripe SDK 初始化完成（key 前缀 {}）",
                props.getApiKey().substring(0, Math.min(7, props.getApiKey().length())));
    }
}
