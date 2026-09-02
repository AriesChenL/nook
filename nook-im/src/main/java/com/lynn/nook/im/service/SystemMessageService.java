package com.lynn.nook.im.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.entity.Conversation;
import com.lynn.nook.im.entity.Message;
import com.lynn.nook.im.mapper.ConversationMapper;
import com.lynn.nook.im.mapper.MessageMapper;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.NewMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 系统消息（content_type=4）写入与推送。
 * <p>内容存结构化 JSON（{@code {"action":..., "operatorId":..., ...}}），由前端按 action 渲染中文，
 * 后端不拼文案、不依赖用户昵称。
 * <p>复用 {@link NewMessageEvent} 事件流，因此与普通消息一样支持多实例 MQ 广播。
 * <p>刻意不依赖 {@link ConversationService}，避免与之形成循环依赖——它只碰 mapper 和事件发布器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMessageService {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final MessageEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 写一条系统消息并在事务提交后推送给指定接收者。
     * 必须在调用方的事务内执行，以保证 afterCommit 在外层事务提交后才推。
     *
     * @param operatorId   触发该事件的用户（作为 sender_id）
     * @param body         结构化内容，至少含 {@code action}
     * @param recipientIds 推送目标 userId 列表（如踢人场景需含被踢者本人）
     */
    public void post(Long conversationId, Long operatorId, Map<String, Object> body, List<Long> recipientIds) {
        Message m = new Message();
        m.setPublicId(UUID.randomUUID().toString());
        m.setConversationId(conversationId);
        m.setSenderId(operatorId);
        m.setContentType(Message.TYPE_SYSTEM);
        m.setContent(serialize(body));
        m.setRecalled((short) 0);
        m.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(m);

        Conversation c = conversationMapper.selectOneById(conversationId);
        if (c != null) {
            c.setLastMessageId(m.getId());
            c.setLastMessageAt(m.getCreatedAt());
            c.setUpdatedAt(m.getCreatedAt());
            conversationMapper.update(c);
        }

        NewMessageEvent event = NewMessageEvent.builder()
                .conversationId(conversationId)
                .memberUserIds(recipientIds)
                .message(MessageVO.from(m))
                .build();
        publishAfterCommit(event);
    }

    private void publishAfterCommit(NewMessageEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishNewMessage(event);
                }
            });
        } else {
            eventPublisher.publishNewMessage(event);
        }
    }

    private String serialize(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException e) {
            log.warn("serialize system message failed: {}", e.getMessage());
            return "{\"action\":\"unknown\"}";
        }
    }
}
