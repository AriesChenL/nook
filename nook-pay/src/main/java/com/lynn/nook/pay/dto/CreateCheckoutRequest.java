package com.lynn.nook.pay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建结账会话请求。前端只传商品/套餐编码，服务端解析成 Stripe Price。
 *
 * @param productCode 商品/套餐编码，对应 nook.stripe.prices 的 key
 * @param quantity    数量，仅一次性付款用；订阅固定为 1。缺省 1
 */
public record CreateCheckoutRequest(

        @NotBlank
        String productCode,

        @Min(1)
        Integer quantity
) {
    public CreateCheckoutRequest {
        if (quantity == null) quantity = 1;
    }
}
