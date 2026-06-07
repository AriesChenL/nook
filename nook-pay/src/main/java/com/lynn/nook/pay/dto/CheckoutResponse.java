package com.lynn.nook.pay.dto;

/**
 * 结账会话创建结果。前端拿 checkoutUrl 直接跳转到 Stripe 托管收银台。
 *
 * @param checkoutUrl   Stripe Checkout 跳转地址
 * @param sessionId     Stripe Checkout Session id
 * @param orderPublicId 本地订单号（订阅场景为 null）
 */
public record CheckoutResponse(String checkoutUrl, String sessionId, String orderPublicId) {
}
