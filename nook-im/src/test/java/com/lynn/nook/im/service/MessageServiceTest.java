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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    private MessageMapper messageMapper;
    private ConversationService conversationService;
    private MessageEventPublisher eventPublisher;
    private MessagePushService pushService;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        conversationService = mock(ConversationService.class);
        eventPublisher = mock(MessageEventPublisher.class);
        pushService = mock(MessagePushService.class);
        messageService = new MessageService(messageMapper, conversationService, eventPublisher, pushService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void send_rejectsBlankContent() {
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(1L);
        req.setContent("   ");

        assertThatThrownBy(() -> messageService.send(10L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.MESSAGE_CONTENT_EMPTY.getCode());

        verifyNoInteractions(messageMapper, eventPublisher);
    }

    @Test
    void send_withoutTransaction_publishesImmediately() {
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(7L);
        req.setContent("hello");
        req.setContentType((short) 1);

        doAnswer(inv -> {
            ((Message) inv.getArgument(0)).setId(99L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        when(conversationService.listMemberIds(7L)).thenReturn(List.of(10L, 20L));

        MessageVO vo = messageService.send(10L, req);

        // 校验成员、写库、回写会话 last_message、发事件
        verify(conversationService).requireMember(7L, 10L);
        ArgumentCaptor<Message> mCap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(mCap.capture());
        assertThat(mCap.getValue().getContent()).isEqualTo("hello");
        verify(conversationService).onMessageSent(eq(7L), eq(99L), any(OffsetDateTime.class));

        ArgumentCaptor<NewMessageEvent> eCap = ArgumentCaptor.forClass(NewMessageEvent.class);
        verify(eventPublisher).publishNewMessage(eCap.capture());
        NewMessageEvent event = eCap.getValue();
        assertThat(event.getConversationId()).isEqualTo(7L);
        assertThat(event.getMemberUserIds()).containsExactly(10L, 20L);
        assertThat(event.getMessage().getId()).isEqualTo(99L);

        assertThat(vo.getId()).isEqualTo(99L);
    }

    @Test
    void send_withinTransaction_defersPublishUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            SendMessageRequest req = new SendMessageRequest();
            req.setConversationId(1L);
            req.setContent("hi");

            doAnswer(inv -> {
                ((Message) inv.getArgument(0)).setId(1L);
                return 1;
            }).when(messageMapper).insert(any(Message.class));
            when(conversationService.listMemberIds(any())).thenReturn(List.of(1L, 2L));

            messageService.send(1L, req);

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
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(1L);
        req.setContent("x");

        doAnswer(inv -> {
            ((Message) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        when(conversationService.listMemberIds(any())).thenReturn(List.of(1L));

        messageService.send(1L, req);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(cap.capture());
        assertThat(cap.getValue().getContentType()).isEqualTo(Message.TYPE_TEXT);
    }

    @Test
    void send_propagatesMemberCheckFailure() {
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(1L);
        req.setContent("x");
        doThrow(new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER))
                .when(conversationService).requireMember(1L, 5L);

        assertThatThrownBy(() -> messageService.send(5L, req))
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
    void recall_marksAndPushesEventToMembers() {
        Message m = new Message();
        m.setId(99L); m.setSenderId(7L); m.setConversationId(42L);
        m.setCreatedAt(OffsetDateTime.now().minusSeconds(10));
        when(messageMapper.selectOneById(99L)).thenReturn(m);
        when(conversationService.listMemberIds(42L)).thenReturn(List.of(7L, 8L));

        messageService.recall(7L, 99L);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).update(cap.capture());
        assertThat(cap.getValue().getRecalled()).isEqualTo((short) 1);
        assertThat(cap.getValue().getRecalledAt()).isNotNull();
        verify(pushService).pushRecall(eq(List.of(7L, 8L)), eq(42L), eq(99L));
    }

    @Test
    void history_appliesQueryAndChecksMembership() {
        when(messageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        messageService.history(1L, 7L, 100L, 9999);

        verify(conversationService).requireMember(7L, 1L);
        verify(messageMapper).selectListByQuery(any(QueryWrapper.class));
    }
}
