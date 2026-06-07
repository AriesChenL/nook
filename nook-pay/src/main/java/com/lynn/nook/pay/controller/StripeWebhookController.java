package com.lynn.nook.pay.controller;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.pay.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe Webhook 接收端。必须在 Gateway 白名单中放行（无 JWT），由 Stripe 签名保证来源可信。
 *
 * <p>状态码语义（Stripe 依此决定是否重试）：
 * <ul>
 *   <li>2xx —— 已处理，不再重试</li>
 *   <li>400 —— 验签失败等永久错误，不应重试</li>
 *   <li>5xx —— 处理时临时故障，Stripe 会按退避策略重试</li>
 * </ul>
 * 注意：此处自行返回状态码，绕开全局异常处理器（后者会把 BusinessException 映射成 200）。
 */
@Slf4j
@RestController
@RequestMapping("/pay/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    /**
     * 用 {@code @RequestBody String} 拿原始报文 —— 验签必须基于未经反序列化的原始字节。
     */
    @PostMapping
    public ResponseEntity<String> receive(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String signature) {
        try {
            webhookService.handle(payload, signature);
            return ResponseEntity.ok("ok");
        } catch (BusinessException e) {
            // 验签失败 / 未配置：永久错误，返回 400，避免无意义重试
            if (e.getCode() == ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID.getCode()
                    || e.getCode() == ResultCode.PAY_NOT_CONFIGURED.getCode()) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
            // 其他业务异常视为可重试
            log.error("Webhook 处理业务异常，返回 500 触发重试", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("retry");
        } catch (Exception e) {
            log.error("Webhook 处理失败，返回 500 触发重试", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("retry");
        }
    }
}
