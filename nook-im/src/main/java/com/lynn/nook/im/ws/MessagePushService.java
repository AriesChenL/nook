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

    /** 推送撤回事件：客户端据此把对应消息从 UI 移除或替换为"已撤回"占位。 */
    public int pushRecall(Collection<Long> memberUserIds, Long conversationId, Long messageId) {
        if (memberUserIds == null || memberUserIds.isEmpty()) return 0;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "recall",
                    "data", Map.of(
                            "conversationId", conversationId,
                            "messageId", messageId
                    )
            ));
            return sessionManager.sendToUsers(memberUserIds, payload);
        } catch (JsonProcessingException e) {
            log.warn("serialize recall payload failed: {}", e.getMessage());
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
