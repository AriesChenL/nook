package com.lynn.nook.ai.service;

import com.lynn.nook.ai.client.EntitlementClient;
import com.lynn.nook.ai.config.NookAiProperties;
import com.lynn.nook.ai.mapper.AiAgentMapper;
import com.lynn.nook.ai.mapper.AiMessageMapper;
import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.common.result.ResultCode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 免费版额度校验。付费用户（nook-pay 判定 active）不受限；
 * <b>权益查询失败时放行不限流</b>（fail-open）——宁可漏限，也不因 nook-pay 抖动误伤付费用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final EntitlementClient entitlementClient;
    private final NookAiProperties props;
    private final AiAgentMapper agentMapper;
    private final AiMessageMapper messageMapper;

    /** 建 Agent 前校验：免费用户 Agent 数不得超过上限。 */
    public void checkCanCreateAgent(Long ownerUserId) {
        if (!isFreeConfirmed(ownerUserId)) return;
        long count = agentMapper.selectCountByQuery(QueryWrapper.create()
                .where("owner_user_id = ?", ownerUserId));
        if (count >= props.getQuota().getFree().getMaxAgents()) {
            throw new BusinessException(ResultCode.AI_QUOTA_AGENTS_EXCEEDED);
        }
    }

    /** 发起对话前校验：免费用户今日对话轮数不得超过上限。 */
    public void checkCanSendMessage(Long ownerUserId) {
        if (!isFreeConfirmed(ownerUserId)) return;
        long todayRounds = messageMapper.selectCountByQuery(QueryWrapper.create()
                .where("role = 'user'")
                .and("created_at >= ?", startOfToday())
                .and("session_id in (select id from ai_chat_session where owner_user_id = ?)", ownerUserId));
        if (todayRounds >= props.getQuota().getFree().getDailyMessages()) {
            throw new BusinessException(ResultCode.AI_QUOTA_MESSAGES_EXCEEDED);
        }
    }

    /**
     * @return true 仅当「确认是免费用户」（nook-pay 返回 active=false）。
     *         付费用户、以及权益查询失败/异常，都返回 false（不限流）。
     */
    private boolean isFreeConfirmed(Long ownerUserId) {
        try {
            Result<EntitlementClient.EntitlementView> r = entitlementClient.entitlement(ownerUserId);
            return r != null && r.getData() != null && !r.getData().active();
        } catch (Exception e) {
            log.warn("查询用户权益失败，放行不限流 user={} err={}", ownerUserId, e.getMessage());
            return false;
        }
    }

    private static OffsetDateTime startOfToday() {
        return OffsetDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
