package com.lynn.nook.pay.dto;

import java.time.OffsetDateTime;

/**
 * 用户权益视图（内部接口）。供其它服务（如 nook-ai）判断该用户是免费还是付费，从而决定是否限流。
 *
 * @param userId 内部数字 userId
 * @param plan   {@code "free"} | {@code "pro"}
 * @param active 是否拥有有效订阅（active / trialing 且未过期）
 * @param until  当前计费周期结束时间；free 时为 null
 */
public record EntitlementVO(Long userId, String plan, boolean active, OffsetDateTime until) {

    public static EntitlementVO free(Long userId) {
        return new EntitlementVO(userId, "free", false, null);
    }

    public static EntitlementVO pro(Long userId, OffsetDateTime until) {
        return new EntitlementVO(userId, "pro", true, until);
    }
}
