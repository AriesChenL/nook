package com.lynn.nook.pay.dto;

/**
 * 结账会话创建结果。前端拿 checkoutUrl 直接跳转到 Stripe 托管收银台。
 *
 * @param checkoutUrl   Stripe Checkout 跳转地址
 * @param sessionId     Stripe Checkout Session id
 */
public record CheckoutResponse(String checkoutUrl, String sessionId) {
}
