package com.lynn.nook.ai.service;

import com.lynn.nook.ai.agent.AgentRuntimeRegistry;
import com.lynn.nook.ai.dto.ChatMessageVO;
import com.lynn.nook.ai.dto.ChatReplyVO;
import com.lynn.nook.ai.dto.ChatRequest;
import com.lynn.nook.ai.entity.AiAgent;
import com.lynn.nook.ai.entity.AiChatSession;
import com.lynn.nook.ai.entity.AiMessage;
import com.lynn.nook.ai.mapper.AiChatSessionMapper;
import com.lynn.nook.ai.mapper.AiMessageMapper;
import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI 对话：取该 Agent 的 HarnessAgent 运行时，以 owner 为 userId（记忆共享域）、
 * sessionId 为对话线程构造 RuntimeContext，调用并返回回复文本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiAgentService agentService;
    private final AiChatSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AgentRuntimeRegistry registry;

    public ChatReplyVO chat(Long ownerUserId, Long agentId, ChatRequest req) {
        AiAgent agent = agentService.requireOwned(ownerUserId, agentId);
        AiChatSession session = resolveSession(ownerUserId, agentId, req.getSessionId());
        Long sessionId = session.getId();

        HarnessAgent ha = registry.get(agent);
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(String.valueOf(ownerUserId))   // 记忆按 owner 跨 Agent 共享（内部数字标识，不脱敏）
                .sessionId("sess-" + sessionId)         // 对话上下文按会话隔离（agentscope 内部标识，仍用数字 id）
                .build();
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(req.getContent()).build();

        try {
            Msg reply = ha.call(userMsg, ctx).block();
            String text = reply == null ? "" : reply.getTextContent();
            // 落库可见历史：用户消息 + 助手回复（成功才记，失败不污染对话流）
            persist(sessionId, "user", req.getContent());
            persist(sessionId, "assistant", text);
            // 对外返回 session public_id（非数字主键）
            return new ChatReplyVO(session.getPublicId(), text);
        } catch (Exception e) {
            log.error("AI 对话失败 agentId={} sessionId={}", agentId, sessionId, e);
            throw new BusinessException(ResultCode.AI_MODEL_ERROR);
        }
    }

    private void persist(Long sessionId, String role, String content) {
        AiMessage m = new AiMessage();
        m.setPublicId(UUID.randomUUID().toString());
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        m.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(m);
    }

    /**
     * 取该 Agent 当前会话的可见历史消息（按时间顺序）。无会话则返回空。
     * 「当前会话」= 最新一条会话（id desc），与 {@link AiAgentService#listSessions} 的排序及前端
     * 「一个 Agent 一段对话」取首条的行为一致；resolveSession 的缺省回退同样取最新，三处保持同源，
     * 避免历史落在 A 会话、读取却取到 B 会话导致「切回来历史为空」。
     */
    public List<ChatMessageVO> listMessages(Long ownerUserId, Long agentId) {
        agentService.requireOwned(ownerUserId, agentId);
        AiChatSession current = latestSession(ownerUserId, agentId);
        if (current == null) return List.of();
        return messageMapper.listBySession(current.getId()).stream()
                .map(ChatMessageVO::from)
                .toList();
    }

    /** 该 owner×agent 最新一条会话（id desc），无则 null。 */
    private AiChatSession latestSession(Long ownerUserId, Long agentId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("agent_id = ?", agentId)
                .and("owner_user_id = ?", ownerUserId)
                .orderBy("id desc")
                .limit(1);
        List<AiChatSession> existing = sessionMapper.selectListByQuery(qw);
        return existing.isEmpty() ? null : existing.get(0);
    }

    /**
     * sessionPublicId 显式则解析回数字主键并校验归属；缺省则取该 owner×agent 最新的会话，无则建一个默认会话。
     * 返回会话实体（含 public_id 与数字主键，分别供对外返回 / 内部 agentscope 使用）。
     */
    private AiChatSession resolveSession(Long ownerUserId, Long agentId, String sessionPublicId) {
        if (sessionPublicId != null && !sessionPublicId.isBlank()) {
            Long sessionId = sessionMapper.selectIdByPublicId(sessionPublicId);
            AiChatSession s = sessionId == null ? null : sessionMapper.selectOneById(sessionId);
            if (s == null || !s.getAgentId().equals(agentId) || !s.getOwnerUserId().equals(ownerUserId)) {
                throw new BusinessException(ResultCode.AI_CHAT_SESSION_NOT_FOUND);
            }
            return s;
        }
        AiChatSession latest = latestSession(ownerUserId, agentId);
        if (latest != null) {
            return latest;
        }
        AiChatSession s = new AiChatSession();
        s.setPublicId(UUID.randomUUID().toString());
        s.setAgentId(agentId);
        s.setOwnerUserId(ownerUserId);
        s.setTitle("默认会话");
        OffsetDateTime now = OffsetDateTime.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        sessionMapper.insert(s);
        return s;
    }
}
