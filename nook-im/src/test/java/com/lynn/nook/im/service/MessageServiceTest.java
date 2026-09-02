package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.dto.SendMessageRequest;
import com.lynn.nook.im.entity.Message;
import com.lynn.nook.im.mapper.MessageMapper;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.NewMessageEvent;
import com.lynn.nook.im.mq.RecallEvent;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    private MessageMapper messageMapper;
    private ConversationService conversationService;
    private MessageEventPublisher eventPublisher;
    private IdResolver idResolver;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        conversationService = mock(ConversationService.class);
        eventPublisher = mock(MessageEventPublisher.class);
        idResolver = mock(IdResolver.class);
        messageService = new MessageService(messageMapper, conversationService, eventPublisher, idResolver);
        // 默认：脱敏装配走真实逻辑的简化桩——把数字 id 映射成 "p<id>"
        lenient().when(idResolver.toMessageVO(any(Message.class), any())).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            MessageVO vo = MessageVO.from(m);
            vo.setConversationId(inv.getArgument(1));
            vo.setSenderId(m.getSenderId() == null ? null : "u" + m.getSenderId());
            return vo;
        });
        lenient().when(idResolver.toMessageVOs(any(), any())).thenAnswer(inv -> {
            List<Message> ms = inv.getArgument(0);
            String cpid = inv.getArgument(1);
            return ms.stream().map(m -> {
                MessageVO vo = MessageVO.from(m);
                vo.setConversationId(cpid);
                vo.setSenderId(m.getSenderId() == null ? null : "u" + m.getSenderId());
                return vo;
            }).toList();
        });
        lenient().when(idResolver.conversationPublicId(anyLong())).thenAnswer(inv -> "c" + inv.getArgument(0));
        lenient().when(idResolver.userPublicIds(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            java.util.Map<Long, String> map = new java.util.HashMap<>();
            if (ids != null) for (Long id : ids) map.put(id, "u" + id);
            return map;
        });
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void send_rejectsBlankContent() {
        SendMessageRequest req = new SendMessageRequest("c1", null, "   ", null, null, null, null);

        assertThatThrownBy(() -> messageService.send(10L, 1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.MESSAGE_CONTENT_EMPTY.getCode());

        verifyNoInteractions(messageMapper, eventPublisher);
    }

    @Test
    void send_withoutTransaction_publishesImmediately() {
        SendMessageRequest req = new SendMessageRequest("c7", (short) 1, "hello", null, null, null, null);

        doAnswer(inv -> {
            ((Message) inv.getArgument(0)).setId(99L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        when(conversationService.listMemberIds(7L)).thenReturn(List.of(10L, 20L));

        MessageVO vo = messageService.send(10L, 7L, req);

        // 校验成员、写库、回写会话 last_message、发事件
        verify(conversationService).requireMember(7L, 10L);
        ArgumentCaptor<Message> mCap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(mCap.capture());
        assertThat(mCap.getValue().getContent()).isEqualTo("hello");
        assertThat(mCap.getValue().getPublicId()).isNotBlank();
        verify(conversationService).onMessageSent(eq(7L), eq(99L), any(OffsetDateTime.class));

        ArgumentCaptor<NewMessageEvent> eCap = ArgumentCaptor.forClass(NewMessageEvent.class);
        verify(eventPublisher).publishNewMessage(eCap.capture());
        NewMessageEvent event = eCap.getValue();
        assertThat(event.getConversationId()).isEqualTo(7L);
        assertThat(event.getMemberUserIds()).containsExactly(10L, 20L);
        // VO 已脱敏：conversationId=入参 public_id，senderId=user public_id
        assertThat(event.getMessage().getConversationId()).isEqualTo("c7");
        assertThat(event.getMessage().getSenderId()).isEqualTo("u10");

        assertThat(vo.getConversationId()).isEqualTo("c7");
        assertThat(vo.getSenderId()).isEqualTo("u10");
    }

    @Test
    void send_withinTransaction_defersPublishUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            SendMessageRequest req = new SendMessageRequest("c1", null, "hi", null, null, null, null);

            doAnswer(inv -> {
                ((Message) inv.getArgument(0)).setId(1L);
                return 1;
            }).when(messageMapper).insert(any(Message.class));
            when(conversationService.listMemberIds(any())).thenReturn(List.of(1L, 2L));

            messageService.send(1L, 1L, req);

            // 事务还没 commit，事件不应该发出去
            verifyNoInteractions(eventPublisher);

            // 模拟事务 commit，注册的回调应该被触发
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCommit();
            }
            verify(eventPublisher).publishNewMessage(any(NewMessageEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void send_defaultsContentTypeToText() {
        SendMessageRequest req = new SendMessageRequest("c1", null, "x", null, null, null, null);

        doAnswer(inv -> {
            ((Message) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        when(conversationService.listMemberIds(any())).thenReturn(List.of(1L));

        messageService.send(1L, 1L, req);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(cap.capture());
        assertThat(cap.getValue().getContentType()).isEqualTo(Message.TYPE_TEXT);
    }

    @Test
    void send_propagatesMemberCheckFailure() {
        SendMessageRequest req = new SendMessageRequest("c1", null, "x", null, null, null, null);
        doThrow(new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER))
                .when(conversationService).requireMember(1L, 5L);

        assertThatThrownBy(() -> messageService.send(5L, 1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_CONVERSATION_MEMBER.getCode());

        verify(messageMapper, never()).insert(any(Message.class));
        verifyNoInteractions(eventPublisher);
    }

    // -------- recall --------

    @Test
    void recall_rejectsWhenMessageMissing() {
        when(messageMapper.selectOneById(99L)).thenReturn(null);
        assertThatThrownBy(() -> messageService.recall(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.MESSAGE_NOT_FOUND.getCode());
    }

    @Test
    void recall_rejectsWhenCallerIsNotSender() {
        Message m = new Message();
        m.setId(99L); m.setSenderId(100L); m.setConversationId(1L);
        m.setCreatedAt(OffsetDateTime.now());
        when(messageMapper.selectOneById(99L)).thenReturn(m);

        assertThatThrownBy(() -> messageService.recall(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.MESSAGE_RECALL_FORBIDDEN.getCode());
        verify(messageMapper, never()).update(any(Message.class));
    }

    @Test
    void recall_rejectsWhenAlreadyRecalled() {
        Message m = new Message();
        m.setId(99L); m.setSenderId(7L); m.setConversationId(1L);
        m.setCreatedAt(OffsetDateTime.now());
        m.setRecalled((short) 1);
        when(messageMapper.selectOneById(99L)).thenReturn(m);

        assertThatThrownBy(() -> messageService.recall(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.MESSAGE_ALREADY_RECALLED.getCode());
    }

    @Test
    void recall_rejectsWhenOutsideTimeWindow() {
        Message m = new Message();
        m.setId(99L); m.setSenderId(7L); m.setConversationId(1L);
        m.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        when(messageMapper.selectOneById(99L)).thenReturn(m);

        assertThatThrownBy(() -> messageService.recall(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.MESSAGE_RECALL_EXPIRED.getCode());
        verify(messageMapper, never()).update(any(Message.class));
    }

    @Test
    void recall_marksAndPublishesRecallEventToMembers() {
        Message m = new Message();
        m.setId(99L); m.setPublicId("msg-99"); m.setSenderId(7L); m.setConversationId(42L);
        m.setCreatedAt(OffsetDateTime.now().minusSeconds(10));
        when(messageMapper.selectOneById(99L)).thenReturn(m);
        when(conversationService.listMemberIds(42L)).thenReturn(List.of(7L, 8L));

        messageService.recall(7L, 99L);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).update(cap.capture());
        assertThat(cap.getValue().getRecalled()).isEqualTo((short) 1);
        assertThat(cap.getValue().getRecalledAt()).isNotNull();

        // 撤回改走 eventPublisher（无事务上下文 → 立即发），多实例下可经 MQ 广播
        ArgumentCaptor<RecallEvent> eCap = ArgumentCaptor.forClass(RecallEvent.class);
        verify(eventPublisher).publishRecall(eCap.capture());
        RecallEvent event = eCap.getValue();
        assertThat(event.getConversationId()).isEqualTo(42L);
        assertThat(event.getMessageId()).isEqualTo(99L);
        // 推给前端的帧用 public_id
        assertThat(event.getConversationPublicId()).isEqualTo("c42");
        assertThat(event.getMessagePublicId()).isEqualTo("msg-99");
        assertThat(event.getMemberUserIds()).containsExactly(7L, 8L);
    }

    // -------- readStatus --------

    @Test
    void readStatus_rejectsWhenMessageMissing() {
        when(messageMapper.selectOneById(5L)).thenReturn(null);
        assertThatThrownBy(() -> messageService.readStatus(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.MESSAGE_NOT_FOUND.getCode());
    }

    @Test
    void readStatus_countsReadersExcludingSender() {
        Message m = new Message();
        m.setId(50L); m.setPublicId("msg-50"); m.setConversationId(9L); m.setSenderId(1L);
        when(messageMapper.selectOneById(50L)).thenReturn(m);
        // 会话 5 人：发送者 1 + 接收方 2,3,4,5；其中 2,3 已读
        when(conversationService.readersOf(9L, 50L, 1L)).thenReturn(List.of(2L, 3L));
        when(conversationService.listMemberIds(9L)).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        when(idResolver.userPublicIds(List.of(2L, 3L))).thenReturn(Map.of(2L, "u2", 3L, "u3"));

        com.lynn.nook.im.dto.ReadStatusVO vo = messageService.readStatus(8L, 50L);

        verify(conversationService).requireMember(9L, 8L);
        assertThat(vo.messageId()).isEqualTo("msg-50");
        assertThat(vo.conversationId()).isEqualTo("c9");
        assertThat(vo.totalRecipients()).isEqualTo(4);   // 5 成员 - 发送者
        assertThat(vo.readCount()).isEqualTo(2);
        assertThat(vo.readerUserIds()).containsExactly("u2", "u3");
    }

    @Test
    void history_appliesQueryAndChecksMembership() {
        when(messageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        messageService.history(1L, 7L, 100L, 9999);

        verify(conversationService).requireMember(7L, 1L);
        verify(messageMapper).selectListByQuery(any(QueryWrapper.class));
    }
}
