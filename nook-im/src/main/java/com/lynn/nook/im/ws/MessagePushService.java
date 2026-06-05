package com.lynn.nook.im.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynn.nook.im.dto.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePushService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * 把新消息推送给该会话所有在线成员（含发送者自己——便于多端同步）。
     * 不在线的成员靠后续历史消息拉取或第三刀的离线推送补齐。
     */
    public int pushNewMessage(Collection<Long> memberUserIds, MessageVO message) {
        if (memberUserIds == null || memberUserIds.isEmpty()) return 0;
        String payload = toPayload("message", message);
        if (payload == null) return 0;
        return sessionManager.sendToUsers(memberUserIds, payload);
    }

    /**
     * 推送撤回事件：客户端据此把对应消息从 UI 移除或替换为"已撤回"占位。
     * 路由按数字 memberUserIds；帧内 conversationId/messageId 用 public_id（脱敏）。
     */
    public int pushRecall(Collection<Long> memberUserIds, String conversationPublicId, String messagePublicId) {
        if (memberUserIds == null || memberUserIds.isEmpty()) return 0;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "recall",
                    "data", Map.of(
                            "conversationId", conversationPublicId,
                            "messageId", messagePublicId
                    )
            ));
            return sessionManager.sendToUsers(memberUserIds, payload);
        } catch (JsonProcessingException e) {
            log.warn("serialize recall payload failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 推送在线状态变更给好友：`{type:"presence", data:{userId, online}}`。
     * 路由按数字 friendUserIds；帧内 userId 用 public_id（脱敏）。
     */
    public int pushPresence(Collection<Long> friendUserIds, String userPublicId, boolean online) {
        if (friendUserIds == null || friendUserIds.isEmpty()) return 0;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "presence",
                    "data", Map.of(
                            "userId", userPublicId,
                            "online", online
                    )
            ));
            return sessionManager.sendToUsers(friendUserIds, payload);
        } catch (JsonProcessingException e) {
            log.warn("serialize presence payload failed: {}", e.getMessage());
            return 0;
        }
    }

    String toPayload(String type, MessageVO message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "data", message
            ));
        } catch (JsonProcessingException e) {
            log.warn("serialize push payload failed: {}", e.getMessage());
            return null;
        }
    }
}
