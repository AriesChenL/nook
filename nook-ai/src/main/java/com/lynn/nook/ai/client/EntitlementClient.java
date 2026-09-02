package com.lynn.nook.ai.client;

import com.lynn.nook.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.OffsetDateTime;

/**
 * 查用户在 nook-pay 的权益（free / pro）。直连 {@code lb://nook-pay}，绕过网关——
 * {@code /pay/internal/**} 是服务间内部接口，入参为内部数字 userId。
 */
@FeignClient(name = "nook-pay")
public interface EntitlementClient {

    @GetMapping("/pay/internal/entitlement/{userId}")
    Result<EntitlementView> entitlement(@PathVariable("userId") Long userId);

    /** nook-pay EntitlementVO 的本地投影。 */
    record EntitlementView(Long userId, String plan, boolean active, OffsetDateTime until) {}
}
