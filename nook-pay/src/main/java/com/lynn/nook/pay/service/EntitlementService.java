package com.lynn.nook.pay.service;

import com.lynn.nook.pay.dto.EntitlementVO;
import com.lynn.nook.pay.entity.Subscription;
import com.lynn.nook.pay.mapper.SubscriptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * 用户权益判定：以本地 Subscription 表（由 Stripe Webhook 同步）为准。
 * 「有效」= 最近一条订阅处于 active/trialing 且计费周期未过期。
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    /** Stripe 订阅状态里视为「已开通」的取值。past_due/unpaid 等按未开通处理。 */
    private static final Set<String> ENTITLED_STATUSES = Set.of("active", "trialing");

    private final SubscriptionMapper subscriptionMapper;

    public EntitlementVO forUser(Long userId) {
        if (userId == null) return EntitlementVO.free(null);

        Subscription sub = subscriptionMapper.selectOneByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .orderBy("updated_at desc")
                .limit(1));

        if (sub == null || sub.getStatus() == null || !ENTITLED_STATUSES.contains(sub.getStatus())) {
            return EntitlementVO.free(userId);
        }
        // 到期时间已过仍兜底判失效（正常 Stripe 会推 customer.subscription.deleted，这里防漏）
        OffsetDateTime until = sub.getCurrentPeriodEnd();
        if (until != null && until.isBefore(OffsetDateTime.now())) {
            return EntitlementVO.free(userId);
        }
        return EntitlementVO.pro(userId, until);
    }
}
