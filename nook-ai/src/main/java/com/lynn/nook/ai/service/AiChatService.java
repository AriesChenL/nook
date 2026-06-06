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
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    /** SSE 帧超时（含模型生成时间）：5 分钟够一轮对话，超时由 MVC 关闭 emitter。 */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    /**
     * 流式对话：用 {@link HarnessAgent#stream} 边生成边把答案增量经 SSE 推给前端。
     * <p>SSE 事件约定：
     * <ul>
     *   <li>{@code delta} —— {@code {"t":"<增量文本>"}}，逐块拼接成完整回复</li>
     *   <li>{@code done}  —— {@code {"sessionId":"<会话 public_id>"}}，首轮对话用于把临时会话落到真实 id</li>
     *   <li>{@code error} —— {@code {"message":"..."}}</li>
     * </ul>
     * 用 {@link HarnessAgent#streamEvents} 的细粒度事件流（2.0 新 API），只取
     * {@link AgentEventType#TEXT_BLOCK_DELTA}（答案 token 增量），累积即完整回复，不混入思考/工具事件。
     * 成功结束后才落库用户消息 + 助手回复，失败不污染历史。
     */
    public SseEmitter chatStream(Long ownerUserId, Long agentId, ChatRequest req) {
        AiAgent agent = agentService.requireOwned(ownerUserId, agentId);
        AiChatSession session = resolveSession(ownerUserId, agentId, req.getSessionId());
        Long sessionId = session.getId();
        String userContent = req.getContent();

        HarnessAgent ha = registry.get(agent);
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(String.valueOf(ownerUserId))
                .sessionId("sess-" + sessionId)
                .build();
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(userContent).build();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        StringBuilder answer = new StringBuilder();  // 累积 TEXT_BLOCK_DELTA = 完整回复

        ha.streamEvents(userMsg, ctx)
                .publishOn(Schedulers.boundedElastic())  // 下游回调（含阻塞 JDBC 落库 / emitter 发送）切到弹性线程
                .subscribe(
                        event -> {
                            // 只取答案文本增量；思考块 / 工具调用 / 起止等事件不推给用户
                            if (event.getType() != AgentEventType.TEXT_BLOCK_DELTA) return;
                            String delta = ((TextBlockDeltaEvent) event).getDelta();
                            if (delta == null || delta.isEmpty()) return;
                            answer.append(delta);
                            try {
                                emitter.send(SseEmitter.event().name("delta").data(Map.of("t", delta)));
                            } catch (IOException e) {
                                throw new IllegalStateException("SSE 发送失败（客户端可能已断开）", e);
                            }
                        },
                        error -> {
                            log.error("AI 流式对话失败 agentId={} sessionId={}", agentId, sessionId, error);
                            try {
                                emitter.send(SseEmitter.event().name("error").data(Map.of("message", "AI 服务异常")));
                            } catch (IOException ignored) {
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            String text = answer.toString();
                            persist(sessionId, "user", userContent);
                            persist(sessionId, "assistant", text);
                            try {
                                emitter.send(SseEmitter.event().name("done")
                                        .data(Map.of("sessionId", session.getPublicId(), "text", text)));
                            } catch (IOException ignored) {
                            }
                            emitter.complete();
                        }
                );
        return emitter;
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
