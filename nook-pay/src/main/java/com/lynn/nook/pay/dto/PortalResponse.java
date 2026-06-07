package com.lynn.nook.pay.dto;

/**
 * Billing Portal 会话结果。前端跳转到 url 让用户自助管理订阅（改套餐/取消/更新卡）。
 */
public record PortalResponse(String url) {
}
