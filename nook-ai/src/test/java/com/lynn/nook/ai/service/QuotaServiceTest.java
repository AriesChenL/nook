package com.lynn.nook.ai.service;

import com.lynn.nook.ai.client.EntitlementClient;
import com.lynn.nook.ai.client.EntitlementClient.EntitlementView;
import com.lynn.nook.ai.config.NookAiProperties;
import com.lynn.nook.ai.mapper.AiAgentMapper;
import com.lynn.nook.ai.mapper.AiMessageMapper;
import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.common.result.ResultCode;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 免费额度校验：pro 用户直接放行；免费用户按上限拦；权益查询失败时 fail-open（不拦）。
 */
class QuotaServiceTest {

    private EntitlementClient entitlementClient;
    private NookAiProperties props;
    private AiAgentMapper agentMapper;
    private AiMessageMapper messageMapper;
    private QuotaService service;

    @BeforeEach
    void setUp() {
        entitlementClient = mock(EntitlementClient.class);
        props = new NookAiProperties(); // 默认 maxAgents=3 dailyMessages=20
        agentMapper = mock(AiAgentMapper.class);
        messageMapper = mock(AiMessageMapper.class);
        service = new QuotaService(entitlementClient, props, agentMapper, messageMapper);
    }

    private void entitlement(boolean active) {
        when(entitlementClient.entitlement(anyLong()))
                .thenReturn(Result.ok(new EntitlementView(1L, active ? "pro" : "free", active, null)));
    }

    // ---------- 建 Agent ----------

    @Test
    void proUser_skipsAgentLimitEntirely() {
        entitlement(true);

        assertThatCode(() -> service.checkCanCreateAgent(1L)).doesNotThrowAnyException();
        verify(agentMapper, never()).selectCountByQuery(any());
    }

    @Test
    void freeUser_underAgentLimit_passes() {
        entitlement(false);
        when(agentMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);

        assertThatCode(() -> service.checkCanCreateAgent(1L)).doesNotThrowAnyException();
    }

    @Test
    void freeUser_atAgentLimit_throws() {
        entitlement(false);
        when(agentMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> service.checkCanCreateAgent(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.AI_QUOTA_AGENTS_EXCEEDED.getCode());
    }

    // ---------- 发消息 ----------

    @Test
    void freeUser_underDailyLimit_passes() {
        entitlement(false);
        when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(5L);

        assertThatCode(() -> service.checkCanSendMessage(1L)).doesNotThrowAnyException();
    }

    @Test
    void freeUser_atDailyLimit_throws() {
        entitlement(false);
        when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(20L);

        assertThatThrownBy(() -> service.checkCanSendMessage(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.AI_QUOTA_MESSAGES_EXCEEDED.getCode());
    }

    // ---------- fail-open ----------

    @Test
    void entitlementLookupThrows_failsOpen() {
        when(entitlementClient.entitlement(anyLong())).thenThrow(new RuntimeException("nook-pay down"));

        assertThatCode(() -> service.checkCanCreateAgent(1L)).doesNotThrowAnyException();
        assertThatCode(() -> service.checkCanSendMessage(1L)).doesNotThrowAnyException();
        verifyNoInteractions(agentMapper, messageMapper);
    }

    @Test
    void entitlementNullBody_failsOpen() {
        when(entitlementClient.entitlement(anyLong())).thenReturn(Result.ok(null));

        assertThatCode(() -> service.checkCanCreateAgent(1L)).doesNotThrowAnyException();
        verifyNoInteractions(agentMapper);
    }
}
