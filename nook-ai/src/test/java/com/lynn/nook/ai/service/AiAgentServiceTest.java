package com.lynn.nook.ai.service;

import com.lynn.nook.ai.agent.AgentRuntimeRegistry;
import com.lynn.nook.ai.dto.AgentVO;
import com.lynn.nook.ai.dto.CreateAgentRequest;
import com.lynn.nook.ai.entity.AiAgent;
import com.lynn.nook.ai.mapper.AiAgentMapper;
import com.lynn.nook.ai.mapper.AiChatSessionMapper;
import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAgentServiceTest {

    @Mock AiAgentMapper agentMapper;
    @Mock AiChatSessionMapper sessionMapper;
    @Mock AgentRuntimeRegistry registry;

    @InjectMocks AiAgentService service;

    @Test
    void create_fillsDefaultsAndReturnsVO() {
        CreateAgentRequest req = new CreateAgentRequest();
        req.setName("小冰");
        req.setPersona("高冷御姐");

        AgentVO vo = service.create(1L, req);

        assertEquals("小冰", vo.getName());
        assertEquals(1L, vo.getOwnerUserId());
        assertEquals("高冷御姐", vo.getPersona());
        assertEquals("deepseek-v4-flash", vo.getModelName(), "未指定模型应回落默认");
        verify(agentMapper).insert(org.mockito.ArgumentMatchers.any(AiAgent.class));
    }

    @Test
    void create_blankName_throws() {
        CreateAgentRequest req = new CreateAgentRequest();
        req.setName("   ");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(1L, req));
        assertEquals(ResultCode.AI_AGENT_NAME_BLANK.getCode(), ex.getCode());
    }

    @Test
    void requireOwned_notFound_throws() {
        when(agentMapper.selectOneById(9L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireOwned(1L, 9L));
        assertEquals(ResultCode.AI_AGENT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void requireOwned_otherOwner_forbidden() {
        AiAgent a = new AiAgent();
        a.setId(9L);
        a.setOwnerUserId(2L);
        when(agentMapper.selectOneById(9L)).thenReturn(a);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireOwned(1L, 9L));
        assertEquals(ResultCode.AI_AGENT_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void delete_invalidatesRuntime() {
        AiAgent a = new AiAgent();
        a.setId(9L);
        a.setOwnerUserId(1L);
        when(agentMapper.selectOneById(9L)).thenReturn(a);

        service.delete(1L, 9L);

        verify(agentMapper).deleteById(9L);
        verify(registry).invalidate(9L);
    }
}
