package com.lynn.nook.ai.service;

import com.lynn.nook.ai.agent.AgentRuntimeRegistry;
import com.lynn.nook.ai.dto.ChatReplyVO;
import com.lynn.nook.ai.dto.ChatRequest;
import com.lynn.nook.ai.entity.AiAgent;
import com.lynn.nook.ai.entity.AiChatSession;
import com.lynn.nook.ai.mapper.AiChatSessionMapper;
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
    private final AgentRuntimeRegistry registry;

    public ChatReplyVO chat(Long ownerUserId, Long agentId, ChatRequest req) {
        AiAgent agent = agentService.requireOwned(ownerUserId, agentId);
        Long sessionId = resolveSession(ownerUserId, agentId, req.getSessionId());

        HarnessAgent ha = registry.get(agent);
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(String.valueOf(ownerUserId))   // 记忆按 owner 跨 Agent 共享
                .sessionId("sess-" + sessionId)         // 对话上下文按会话隔离
                .build();
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(req.getContent()).build();

        try {
            Msg reply = ha.call(userMsg, ctx).block();
            String text = reply == null ? "" : reply.getTextContent();
            return new ChatReplyVO(sessionId, text);
        } catch (Exception e) {
            log.error("AI 对话失败 agentId={} sessionId={}", agentId, sessionId, e);
            throw new BusinessException(ResultCode.AI_MODEL_ERROR);
        }
    }

    /** sessionId 显式则校验归属；缺省则取该 owner×agent 最早的会话，无则建一个默认会话。 */
    private Long resolveSession(Long ownerUserId, Long agentId, Long sessionId) {
        if (sessionId != null) {
            AiChatSession s = sessionMapper.selectOneById(sessionId);
            if (s == null || !s.getAgentId().equals(agentId) || !s.getOwnerUserId().equals(ownerUserId)) {
                throw new BusinessException(ResultCode.AI_CHAT_SESSION_NOT_FOUND);
            }
            return sessionId;
        }
        QueryWrapper qw = QueryWrapper.create()
                .where("agent_id = ?", agentId)
                .and("owner_user_id = ?", ownerUserId)
                .orderBy("id asc")
                .limit(1);
        List<AiChatSession> existing = sessionMapper.selectListByQuery(qw);
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }
        AiChatSession s = new AiChatSession();
        s.setAgentId(agentId);
        s.setOwnerUserId(ownerUserId);
        s.setTitle("默认会话");
        OffsetDateTime now = OffsetDateTime.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        sessionMapper.insert(s);
        return s.getId();
    }
}
