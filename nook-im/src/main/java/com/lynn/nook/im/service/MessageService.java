package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.dto.SendMessageRequest;
import com.lynn.nook.im.entity.Message;
import com.lynn.nook.im.mapper.MessageMapper;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.NewMessageEvent;
import com.lynn.nook.im.ws.MessagePushService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    /** 消息可撤回的时间窗口：2 分钟。超过返回 MESSAGE_RECALL_EXPIRED。 */
    public static final Duration RECALL_WINDOW = Duration.ofMinutes(2);

    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final MessageEventPublisher eventPublisher;
    private final MessagePushService pushService;

    @Transactional
    public MessageVO send(Long senderId, SendMessageRequest req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(ResultCode.MESSAGE_CONTENT_EMPTY);
        }
        conversationService.requireMember(req.getConversationId(), senderId);

        Message m = new Message();
        m.setConversationId(req.getConversationId());
        m.setSenderId(senderId);
        m.setContentType(req.getContentType() == null ? Message.TYPE_TEXT : req.getContentType());
        m.setContent(req.getContent());
        m.setRecalled((short) 0);
        m.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(m);

        conversationService.onMessageSent(req.getConversationId(), m.getId(), m.getCreatedAt());

        MessageVO vo = MessageVO.from(m);
        NewMessageEvent event = NewMessageEvent.builder()
                .conversationId(req.getConversationId())
                .memberUserIds(conversationService.listMemberIds(req.getConversationId()))
                .message(vo)
                .build();
        publishAfterCommit(event);
        return vo;
    }

    /** 事务提交后才发事件——避免接收方在事务可见前用 history 拉不到记录。 */
    private void publishAfterCommit(NewMessageEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishNewMessage(event);
                }
            });
        } else {
            // 无事务上下文（如单测中直接调用）：立即发，行为退化为同步
            eventPublisher.publishNewMessage(event);
        }
    }

    /**
     * 拉取历史消息：按 id 倒序分页。
     * @param beforeId 可选，仅返回 id < beforeId 的消息；null 表示从最新开始
     */
    public List<MessageVO> history(Long userId, Long conversationId, Long beforeId, int limit) {
        conversationService.requireMember(conversationId, userId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        QueryWrapper qw = QueryWrapper.create()
                .where("conversation_id = ?", conversationId);
        if (beforeId != null && beforeId > 0) {
            qw.and("id < ?", beforeId);
        }
        qw.orderBy("id desc").limit(safeLimit);
        return messageMapper.selectListByQuery(qw).stream()
                .map(MessageVO::from)
                .toList();
    }

    /** 撤回消息：仅发送者本人，且在 RECALL_WINDOW 时间内。 */
    @Transactional
    public void recall(Long userId, Long messageId) {
        Message m = messageMapper.selectOneById(messageId);
        if (m == null) throw new BusinessException(ResultCode.MESSAGE_NOT_FOUND);
        if (!userId.equals(m.getSenderId())) {
            throw new BusinessException(ResultCode.MESSAGE_RECALL_FORBIDDEN);
        }
        if (m.getRecalled() != null && m.getRecalled() == 1) {
            throw new BusinessException(ResultCode.MESSAGE_ALREADY_RECALLED);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (m.getCreatedAt() != null
                && Duration.between(m.getCreatedAt(), now).compareTo(RECALL_WINDOW) > 0) {
            throw new BusinessException(ResultCode.MESSAGE_RECALL_EXPIRED);
        }

        m.setRecalled((short) 1);
        m.setRecalledAt(now);
        messageMapper.update(m);

        // 推送撤回事件给会话所有在线成员
        List<Long> members = conversationService.listMemberIds(m.getConversationId());
        pushService.pushRecall(members, m.getConversationId(), m.getId());
    }
}
