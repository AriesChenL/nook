package com.lynn.nook.pay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建结账会话请求。前端只传商品/套餐编码，服务端解析成 Stripe Price。
 */
@Data
public class CreateCheckoutRequest {

    /** 商品/套餐编码，对应 nook.stripe.prices 的 key。 */
    @NotBlank
    private String productCode;

    /** 数量，仅一次性付款用；订阅固定为 1。 */
    @Min(1)
    private Integer quantity = 1;
}
