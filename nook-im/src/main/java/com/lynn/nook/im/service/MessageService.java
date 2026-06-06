package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.dto.ReadStatusVO;
import com.lynn.nook.im.dto.SendMessageRequest;
import com.lynn.nook.im.entity.Message;
import com.lynn.nook.im.mapper.MessageMapper;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.NewMessageEvent;
import com.lynn.nook.im.mq.RecallEvent;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    /** 消息可撤回的时间窗口：5 分钟。超过返回 MESSAGE_RECALL_EXPIRED。 */
    public static final Duration RECALL_WINDOW = Duration.ofMinutes(5);

    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final MessageEventPublisher eventPublisher;
    private final IdResolver idResolver;

    @Transactional
    public MessageVO send(Long senderId, Long conversationId, SendMessageRequest req) {
        short type = req.getContentType() == null ? Message.TYPE_TEXT : req.getContentType();
        boolean isFile = type == Message.TYPE_IMAGE || type == Message.TYPE_FILE;
        if (isFile) {
            // 文件消息：必须带下载地址，content 允许为空（用 fileName 兜底）
            if (req.getFileUrl() == null || req.getFileUrl().isBlank()) {
                throw new BusinessException(ResultCode.FILE_META_MISSING);
            }
        } else if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(ResultCode.MESSAGE_CONTENT_EMPTY);
        }
        conversationService.requireMember(conversationId, senderId);

        Message m = new Message();
        m.setPublicId(UUID.randomUUID().toString());
        m.setConversationId(conversationId);
        m.setSenderId(senderId);
        m.setContentType(type);
        // content 列 NOT NULL：文件消息 content 为空时用文件名兜底（也便于会话列表预览）
        String content = req.getContent();
        if (content == null || content.isBlank()) {
            content = isFile && req.getFileName() != null ? req.getFileName() : "";
        }
        m.setContent(content);
        if (isFile) {
            m.setFileUrl(req.getFileUrl());
            m.setFileName(req.getFileName());
            m.setFileSize(req.getFileSize());
            m.setMediaType(req.getMediaType());
        }
        m.setRecalled((short) 0);
        m.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(m);

        conversationService.onMessageSent(conversationId, m.getId(), m.getCreatedAt());

        // 脱敏：id=消息 public_id、conversationId=会话 public_id、senderId=发送者 user public_id
        MessageVO vo = idResolver.toMessageVO(m, req.getConversationId());
        NewMessageEvent event = NewMessageEvent.builder()
                .conversationId(conversationId)
                .memberUserIds(conversationService.listMemberIds(conversationId))
                .message(vo)
                .build();
        runAfterCommit(() -> eventPublisher.publishNewMessage(event));
        return vo;
    }

    /** 事务提交后才执行——避免接收方在事务可见前用 history 拉不到记录。无事务上下文（如单测）则立即执行。 */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
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
        List<Message> rows = messageMapper.selectListByQuery(qw);
        String convPublicId = idResolver.conversationPublicId(conversationId);
        return idResolver.toMessageVOs(rows, convPublicId);
    }

    /** 消息已读状态（群聊已读人数）：调用方需为会话成员。 */
    public ReadStatusVO readStatus(Long userId, Long messageId) {
        Message m = messageMapper.selectOneById(messageId);
        if (m == null) throw new BusinessException(ResultCode.MESSAGE_NOT_FOUND);
        conversationService.requireMember(m.getConversationId(), userId);

        List<Long> readers = conversationService.readersOf(m.getConversationId(), messageId, m.getSenderId());
        int totalRecipients = (int) conversationService.listMemberIds(m.getConversationId()).stream()
                .filter(id -> !id.equals(m.getSenderId()))
                .count();
        Map<Long, String> readerPub = idResolver.userPublicIds(readers);
        return ReadStatusVO.builder()
                .messageId(m.getPublicId())
                .conversationId(idResolver.conversationPublicId(m.getConversationId()))
                .totalRecipients(totalRecipients)
                .readCount(readers.size())
                .readerUserIds(readers.stream().map(readerPub::get).toList())
                .build();
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

        // 撤回事件走与新消息一致的发布链路：本地直推或 MQ broadcasting（多实例），事务提交后才发
        // 帧内 id 用 public_id（脱敏）；memberUserIds 保持数字供内部路由
        RecallEvent event = RecallEvent.builder()
                .conversationId(m.getConversationId())
                .messageId(m.getId())
                .conversationPublicId(idResolver.conversationPublicId(m.getConversationId()))
                .messagePublicId(m.getPublicId())
                .memberUserIds(conversationService.listMemberIds(m.getConversationId()))
                .build();
        runAfterCommit(() -> eventPublisher.publishRecall(event));
    }
}
