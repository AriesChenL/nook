package com.lynn.nook.ai.service;

import com.lynn.nook.ai.agent.PersonaMiddleware;
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
import tools.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 对话：取该 Agent 的 {@link ChatUiChannel}（走 agentscope Gateway），以
 * {@code SendOptions.of(ownerUserId, sessionId)} 标识说话人与对话线程——Gateway 据此做
 * 会话管理与 per-session 排队，记忆按 owner 跨 Agent 共享。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiAgentService agentService;
    private final QuotaService quotaService;
    private final AiChatSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final ChatUiChannel nookChatChannel;   // 单例，走 agentscope Gateway
    private final ObjectMapper objectMapper;

    public ChatReplyVO chat(Long ownerUserId, Long agentId, ChatRequest req) {
        AiAgent agent = agentService.requireOwned(ownerUserId, agentId);
        quotaService.checkCanSendMessage(ownerUserId);
        AiChatSession session = resolveSession(ownerUserId, agentId, req.sessionId());
        Long sessionId = session.getId();

        SendOptions opts = sendOptions(agent, sessionId);

        try {
            Msg reply = nookChatChannel.send(opts, req.content()).block();
            String text = reply == null ? "" : reply.getTextContent();
            // 落库可见历史：用户消息 + 助手回复（成功才记，失败不污染对话流）
            persist(sessionId, "user", req.content(), null);
            persist(sessionId, "assistant", text, null);  // 非流式无过程
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
     * 流式对话：经 {@link ChatUiChannel#sendStream} 边生成边把答案增量经 SSE 推给前端。
     * <p>SSE 事件约定（让前端看到完整推理过程）：
     * <ul>
     *   <li>{@code delta}    —— {@code {"t":"<增量文本>"}}，答案 token 增量，逐块拼接成完整回复</li>
     *   <li>{@code thinking} —— {@code {"id":"<blockId>","t":"<增量文本>"}}，模型思考/推理增量（不落库；id 区分多段思考）</li>
     *   <li>{@code tool}     —— 工具调用过程，按 {@code phase} 区分：
     *       {@code call}（{@code {"phase":"call","id":...,"name":...}} 起调）、
     *       {@code args}（{@code {"phase":"args","id":...,"t":...}} 入参增量）、
     *       {@code result}（{@code {"phase":"result","id":...,"t":...}} 结果增量）、
     *       {@code end}（{@code {"phase":"end","id":...}} 结束）；{@code id} 为 toolCallId，前端按其聚合</li>
     *   <li>{@code done}     —— {@code {"sessionId":"<会话 public_id>","text":"<全文>"}}，首轮用于把临时会话落到真实 id</li>
     *   <li>{@code error}    —— {@code {"message":"..."}}</li>
     * </ul>
     * 用 {@link ChatUiChannel#sendStream} 的细粒度事件流（经 Gateway 路由，等价 streamEvents 但带
     * 会话管理 + per-session 排队）：{@code TEXT_BLOCK_DELTA} 累积为完整回复，思考 / 工具事件仅透传给
     * 前端做过程展示。生命周期 / 模型调用 / start-end 标记等其余事件不外发。
     * 成功结束后才落库用户消息 + 助手回复（仅答案正文），失败不污染历史。
     */
    public SseEmitter chatStream(Long ownerUserId, Long agentId, ChatRequest req) {
        AiAgent agent = agentService.requireOwned(ownerUserId, agentId);
        quotaService.checkCanSendMessage(ownerUserId);
        AiChatSession session = resolveSession(ownerUserId, agentId, req.sessionId());
        Long sessionId = session.getId();
        String userContent = req.content();

        SendOptions opts = sendOptions(agent, sessionId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        StringBuilder answer = new StringBuilder();  // 累积 TEXT_BLOCK_DELTA = 完整回复
        List<Map<String, Object>> steps = new ArrayList<>();  // 同步聚合推理过程（思考分段 + 工具调用），结束随回复落库

        nookChatChannel.sendStream(opts, userContent)
                .publishOn(Schedulers.boundedElastic())  // 下游回调（含阻塞 JDBC 落库 / emitter 发送）切到弹性线程
                .subscribe(
                        event -> {
                            // 答案增量累积为完整回复（落库）；思考 / 工具事件仅透传做过程展示；其余事件丢弃
                            switch (event) {
                                case TextBlockDeltaEvent e -> {
                                    String delta = e.getDelta();
                                    if (delta == null || delta.isEmpty()) return;
                                    answer.append(delta);
                                    emit(emitter, "delta", Map.of("t", delta));
                                }
                                case ThinkingBlockDeltaEvent e -> {
                                    String delta = e.getDelta();
                                    if (delta == null || delta.isEmpty()) return;
                                    // 带 blockId：前端按其区分多段思考（think → 工具 → think 不被合并）
                                    emit(emitter, "thinking", Map.of("id", nz(e.getBlockId()), "t", delta));
                                    appendThink(steps, nz(e.getBlockId()), delta);
                                }
                                case ToolCallStartEvent e -> {
                                    emit(emitter, "tool", Map.of(
                                            "phase", "call", "id", nz(e.getToolCallId()), "name", nz(e.getToolCallName())));
                                    addToolStep(steps, nz(e.getToolCallId()), nz(e.getToolCallName()));
                                }
                                case ToolCallDeltaEvent e -> {
                                    String delta = e.getDelta();
                                    if (delta == null || delta.isEmpty()) return;
                                    emit(emitter, "tool", Map.of("phase", "args", "id", nz(e.getToolCallId()), "t", delta));
                                    appendToolField(steps, nz(e.getToolCallId()), "args", delta);
                                }
                                case ToolResultTextDeltaEvent e -> {
                                    String delta = e.getDelta();
                                    if (delta == null || delta.isEmpty()) return;
                                    emit(emitter, "tool", Map.of("phase", "result", "id", nz(e.getToolCallId()), "t", delta));
                                    appendToolField(steps, nz(e.getToolCallId()), "result", delta);
                                }
                                case ToolResultEndEvent e ->
                                        emit(emitter, "tool", Map.of("phase", "end", "id", nz(e.getToolCallId())));
                                default -> { /* 生命周期 / 模型调用 / start-end 标记等不外发 */ }
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
                            persist(sessionId, "user", userContent, null);
                            persist(sessionId, "assistant", text, serializeSteps(steps));
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

    /** 流式过程中发一帧 SSE；失败（客户端断开）抛出，由 reactor error 通道走 error 回调收尾。 */
    private static void emit(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 发送失败（客户端可能已断开）", e);
        }
    }

    /** null → 空串，供 {@link Map#of} 用（其不接受 null value）。 */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * Gateway 发送选项：owner 作为说话人（记忆共享域）、会话数字主键作为对话线程，
     * persona 作为当轮属性（由 {@link PersonaMiddleware} 注入系统提示）。
     * Gateway 据此派生稳定的内部 session id 并做 per-session 排队。均为内部数字标识，不脱敏。
     */
    private static SendOptions sendOptions(AiAgent agent, Long sessionId) {
        return SendOptions.of(String.valueOf(agent.getOwnerUserId()), String.valueOf(sessionId))
                .withAttribute(PersonaMiddleware.PERSONA_ATTR,
                        agent.getPersona() == null ? "" : agent.getPersona());
    }

    private void persist(Long sessionId, String role, String content, String trace) {
        AiMessage m = new AiMessage();
        m.setPublicId(UUID.randomUUID().toString());
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        m.setTrace(trace);
        m.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(m);
    }

    /** steps 空 → null（不存噪声）；序列化失败降级为 null，不阻断回复落库。前后端 step 结构需一致。 */
    private String serializeSteps(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            log.warn("序列化推理过程失败 steps={}", steps.size(), e);
            return null;
        }
    }

    // ── 推理过程聚合（与前端 AiView 逻辑对齐，保证落库与实时展示同形）──

    /** 思考增量：末步仍是同段思考（同 blockId 或上游未给 id）则续写，否则另起一段。 */
    private static void appendThink(List<Map<String, Object>> steps, String id, String delta) {
        Map<String, Object> last = steps.isEmpty() ? null : steps.getLast();
        if (last != null && "think".equals(last.get("kind")) && (id.isEmpty() || id.equals(last.get("id")))) {
            last.put("text", (String) last.get("text") + delta);
            return;
        }
        Map<String, Object> step = new HashMap<>();
        step.put("kind", "think");
        step.put("id", id);
        step.put("text", delta);
        steps.add(step);
    }

    /** 工具起调：每次 TOOL_CALL_START 都新开一步（同名 / 空 id 也不合并）。 */
    private static void addToolStep(List<Map<String, Object>> steps, String id, String name) {
        Map<String, Object> step = new HashMap<>();
        step.put("kind", "tool");
        step.put("id", id);
        step.put("name", name.isEmpty() ? "工具" : name);
        step.put("args", "");
        step.put("result", "");
        steps.add(step);
    }

    /** 工具入参 / 结果增量：归到匹配 id 的最近工具步（id 空则落到最后一个工具步）；无则兜底补一步。 */
    private static void appendToolField(List<Map<String, Object>> steps, String id, String field, String delta) {
        for (Map<String, Object> s : steps.reversed()) {
            if ("tool".equals(s.get("kind")) && (id.isEmpty() || id.equals(s.get("id")))) {
                s.put(field, (String) s.get(field) + delta);
                return;
            }
        }
        Map<String, Object> step = new HashMap<>();
        step.put("kind", "tool");
        step.put("id", id);
        step.put("name", "工具");
        step.put("args", "");
        step.put("result", "");
        step.put(field, delta);
        steps.add(step);
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
        return existing.isEmpty() ? null : existing.getFirst();
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
