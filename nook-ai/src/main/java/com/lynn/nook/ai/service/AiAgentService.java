package com.lynn.nook.ai.service;

import com.lynn.nook.ai.dto.AgentVO;
import com.lynn.nook.ai.dto.ChatSessionVO;
import com.lynn.nook.ai.dto.CreateAgentRequest;
import com.lynn.nook.ai.dto.CreateSessionRequest;
import com.lynn.nook.ai.dto.UpdateAgentRequest;
import com.lynn.nook.ai.entity.AiAgent;
import com.lynn.nook.ai.entity.AiChatSession;
import com.lynn.nook.ai.mapper.AiAgentMapper;
import com.lynn.nook.ai.mapper.AiChatSessionMapper;
import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI Agent 的管理（CRUD）+ 对话会话线程管理。所有操作校验 owner。
 */
@Service
@RequiredArgsConstructor
public class AiAgentService {

    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    private final AiAgentMapper agentMapper;
    private final AiChatSessionMapper sessionMapper;
    private final QuotaService quotaService;

    public AgentVO create(Long ownerUserId, CreateAgentRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ResultCode.AI_AGENT_NAME_BLANK);
        }
        quotaService.checkCanCreateAgent(ownerUserId);
        AiAgent a = new AiAgent();
        a.setPublicId(UUID.randomUUID().toString());
        a.setOwnerUserId(ownerUserId);
        a.setName(req.getName().trim());
        a.setPersona(req.getPersona() == null ? "" : req.getPersona());
        a.setAvatarUrl(req.getAvatarUrl());
        a.setModelName(blankTo(req.getModelName(), DEFAULT_MODEL));
        a.setStatus((short) 1);
        OffsetDateTime now = OffsetDateTime.now();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        agentMapper.insert(a);
        return AgentVO.from(a);
    }

    public List<AgentVO> listMine(Long ownerUserId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("owner_user_id = ?", ownerUserId)
                .orderBy("id desc");
        return agentMapper.selectListByQuery(qw).stream().map(AgentVO::from).toList();
    }

    public AgentVO get(Long ownerUserId, Long agentId) {
        return AgentVO.from(requireOwned(ownerUserId, agentId));
    }

    public AgentVO update(Long ownerUserId, Long agentId, UpdateAgentRequest req) {
        AiAgent a = requireOwned(ownerUserId, agentId);
        if (req.getName() != null && !req.getName().isBlank()) {
            a.setName(req.getName().trim());
        }
        if (req.getPersona() != null) {
            a.setPersona(req.getPersona());   // 下一轮对话即经 PersonaMiddleware 生效，无需重建运行时
        }
        if (req.getAvatarUrl() != null) {
            a.setAvatarUrl(req.getAvatarUrl());
        }
        a.setUpdatedAt(OffsetDateTime.now());
        agentMapper.update(a);
        return AgentVO.from(a);
    }

    public void delete(Long ownerUserId, Long agentId) {
        requireOwned(ownerUserId, agentId);
        agentMapper.deleteById(agentId);
    }

    /** agent public_id → 数字主键 id；解析不到抛资源不存在。 */
    public Long resolveAgentId(String agentPublicId) {
        Long id = agentMapper.selectIdByPublicId(agentPublicId);
        if (id == null) {
            throw new BusinessException(ResultCode.AI_AGENT_NOT_FOUND);
        }
        return id;
    }

    /** session public_id → 数字主键 id；解析不到抛资源不存在。 */
    public Long resolveSessionId(String sessionPublicId) {
        Long id = sessionMapper.selectIdByPublicId(sessionPublicId);
        if (id == null) {
            throw new BusinessException(ResultCode.AI_CHAT_SESSION_NOT_FOUND);
        }
        return id;
    }

    /** 校验 Agent 存在且属于该 owner，返回实体。 */
    public AiAgent requireOwned(Long ownerUserId, Long agentId) {
        AiAgent a = agentMapper.selectOneById(agentId);
        if (a == null) {
            throw new BusinessException(ResultCode.AI_AGENT_NOT_FOUND);
        }
        if (!a.getOwnerUserId().equals(ownerUserId)) {
            throw new BusinessException(ResultCode.AI_AGENT_FORBIDDEN);
        }
        return a;
    }

    public ChatSessionVO createSession(Long ownerUserId, Long agentId, CreateSessionRequest req) {
        AiAgent agent = requireOwned(ownerUserId, agentId);
        AiChatSession s = new AiChatSession();
        s.setPublicId(UUID.randomUUID().toString());
        s.setAgentId(agentId);
        s.setOwnerUserId(ownerUserId);
        s.setTitle(req == null ? null : req.getTitle());
        OffsetDateTime now = OffsetDateTime.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        sessionMapper.insert(s);
        return ChatSessionVO.from(s, agent.getPublicId());
    }

    public List<ChatSessionVO> listSessions(Long ownerUserId, Long agentId) {
        AiAgent agent = requireOwned(ownerUserId, agentId);
        QueryWrapper qw = QueryWrapper.create()
                .where("agent_id = ?", agentId)
                .orderBy("id desc");
        return sessionMapper.selectListByQuery(qw).stream()
                .map(s -> ChatSessionVO.from(s, agent.getPublicId()))
                .toList();
    }

    private static String blankTo(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }
}
