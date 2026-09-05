package com.lynn.nook.pay.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建结账会话请求。前端只传商品/套餐编码，服务端解析成 Stripe Price。
 *
 * @param productCode 商品/套餐编码，对应 nook.stripe.prices 的 key
 */
public record CreateCheckoutRequest(
        @NotBlank
        String productCode
) {
}
