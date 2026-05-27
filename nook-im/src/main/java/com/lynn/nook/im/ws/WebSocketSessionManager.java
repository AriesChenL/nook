package com.lynn.nook.im.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内的 WebSocket 会话注册中心。
 * 同一 userId 允许多端同时在线，每个会话独立存储。
 * 注意：本实现仅覆盖单实例；多实例广播需要在第三刀通过 MQ 解决。
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    public static final String ATTR_USER_ID = "userId";

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        sessionsByUser
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
        log.debug("ws registered: userId={}, sessionId={}, total={}",
                userId, session.getId(), sessionsByUser.get(userId).size());
    }

    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        if (set == null) return;
        set.remove(session);
        if (set.isEmpty()) sessionsByUser.remove(userId);
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        return set != null && !set.isEmpty();
    }

    public int sessionCount(Long userId) {
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        return set == null ? 0 : set.size();
    }

    /** 向指定用户的所有在线会话推送 payload。返回成功发送的会话数。 */
    public int sendToUser(Long userId, String payload) {
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        if (set == null || set.isEmpty()) return 0;
        TextMessage msg = new TextMessage(payload);
        int sent = 0;
        for (WebSocketSession s : set) {
            if (!s.isOpen()) continue;
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
                sent++;
            } catch (IOException e) {
                log.warn("ws send failed: userId={}, sessionId={}, err={}", userId, s.getId(), e.getMessage());
            }
        }
        return sent;
    }

    /** 向一批用户广播相同 payload。返回成功送达的总会话数。 */
    public int sendToUsers(Collection<Long> userIds, String payload) {
        if (userIds == null || userIds.isEmpty()) return 0;
        int total = 0;
        for (Long uid : userIds) total += sendToUser(uid, payload);
        return total;
    }
}
