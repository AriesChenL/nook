package com.lynn.nook.im.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynn.nook.im.service.PresenceBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 入口 WebSocket 处理器。
 * 当前第一版只处理：连接建立/关闭、客户端心跳 ping → pong。
 * 业务消息推送方向是 服务端 → 客户端；客户端发消息仍走 HTTP POST /im/messages。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final PresenceService presenceService;
    private final PresenceBroadcastService presenceBroadcastService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userIdOf(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionManager.register(userId, session);
        presenceService.markOnline(userId);
        // 本用户首个连接 → 上线，广播给好友
        if (sessionManager.sessionCount(userId) == 1) {
            presenceBroadcastService.onOnline(userId);
        }
        sendJson(session, Map.of("type", "ready", "userId", userId));
        log.debug("ws opened: userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 心跳：客户端发 {"type":"ping"} → 回 {"type":"pong","ts":...}
        String payload = message.getPayload();
        if (payload != null && payload.contains("\"ping\"")) {
            sendJson(session, Map.of("type", "pong", "ts", System.currentTimeMillis()));
        }
        // 其他类型暂忽略；消息发送走 HTTP，避免 WS 上下文里再做业务校验。
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId == null) return;
        sessionManager.unregister(userId, session);
        if (sessionManager.sessionCount(userId) == 0) {
            presenceService.markOffline(userId);
            // 最后一个连接断开 → 下线，广播给好友
            presenceBroadcastService.onOffline(userId);
        }
        log.debug("ws closed: userId={}, sessionId={}, status={}", userId, session.getId(), status);
    }

    private Long userIdOf(WebSocketSession session) {
        Object v = session.getAttributes().get(WebSocketSessionManager.ATTR_USER_ID);
        return (v instanceof Long l) ? l : null;
    }

    private void sendJson(WebSocketSession session, Object obj) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(obj)));
            }
        } catch (Exception e) {
            log.warn("ws sendJson failed: {}", e.getMessage());
        }
    }
}
