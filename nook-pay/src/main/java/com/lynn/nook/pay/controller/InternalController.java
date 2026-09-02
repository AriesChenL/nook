package com.lynn.nook.pay.controller;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.pay.dto.EntitlementVO;
import com.lynn.nook.pay.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间内部接口：其它服务经 {@code lb://nook-pay} 直连（绕过网关），入参为内部数字 userId。
 * 不读 X-User-Id、不做归属校验——只返回「某用户是否付费」这类非敏感信息。
 */
@RestController
@RequestMapping("/pay/internal")
@RequiredArgsConstructor
public class InternalController {

    private final EntitlementService entitlementService;

    /** 查某用户权益（free / pro），供 nook-ai 等按套餐限流。 */
    @GetMapping("/entitlement/{userId}")
    public Result<EntitlementVO> entitlement(@PathVariable("userId") Long userId) {
        return Result.ok(entitlementService.forUser(userId));
    }
}
