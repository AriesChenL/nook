package com.lynn.nook.pay.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.pay.dto.CheckoutResponse;
import com.lynn.nook.pay.dto.CreateCheckoutRequest;
import com.lynn.nook.pay.dto.InvoiceVO;
import com.lynn.nook.pay.dto.PortalResponse;
import com.lynn.nook.pay.dto.SubscriptionVO;
import com.lynn.nook.pay.dto.SyncSubscriptionRequest;
import com.lynn.nook.pay.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 结账与订阅相关接口。需登录（Gateway 注入 X-User-Id）。
 */
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class CheckoutController {

    private final PaymentService paymentService;

    /** 订阅：返回 Stripe Checkout 跳转地址。 */
    @PostMapping("/checkout/subscription")
    public Result<CheckoutResponse> subscription(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                                 @RequestHeader(value = "Idempotency-Key", required = false)
                                                 String idempotencyKey,
                                                 @Valid @RequestBody CreateCheckoutRequest req) {
        return Result.ok(paymentService.createSubscriptionCheckout(userId, req, idempotencyKey));
    }

    /** 当前订阅信息（无订阅时 data 为 null）。 */
    @GetMapping("/subscription")
    public Result<SubscriptionVO> currentSubscription(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(paymentService.getActiveSubscription(userId));
    }

    /** 打开 Billing Portal 自助管理订阅。 */
    @PostMapping("/portal")
    public Result<PortalResponse> portal(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(paymentService.createBillingPortal(userId));
    }

    /** Checkout 成功回跳后的主动对账，解决 Webhook 到达前页面短暂显示 Free 的问题。 */
    @PostMapping("/subscription/sync")
    public Result<SubscriptionVO> sync(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                       @Valid @RequestBody SyncSubscriptionRequest req) {
        return Result.ok(paymentService.syncSubscription(userId, req.sessionId()));
    }

    /** 当前用户最近 50 张账单，包含 Stripe 托管账单页与 PDF 地址。 */
    @GetMapping("/invoices")
    public Result<List<InvoiceVO>> invoices(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(paymentService.listInvoices(userId));
    }
}
