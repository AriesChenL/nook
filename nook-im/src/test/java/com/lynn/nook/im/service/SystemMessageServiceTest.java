package com.lynn.nook.im.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.lynn.nook.im.entity.Conversation;
import com.lynn.nook.im.entity.Message;
import com.lynn.nook.im.mapper.ConversationMapper;
import com.lynn.nook.im.mapper.MessageMapper;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.NewMessageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemMessageServiceTest {

    private MessageMapper messageMapper;
    private ConversationMapper conversationMapper;
    private MessageEventPublisher eventPublisher;
    private SystemMessageService service;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        conversationMapper = mock(ConversationMapper.class);
        eventPublisher = mock(MessageEventPublisher.class);
        service = new SystemMessageService(messageMapper, conversationMapper, eventPublisher, JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void post_insertsSystemMessageAndUpdatesConversationAndPublishes() {
        doAnswer(inv -> { ((Message) inv.getArgument(0)).setId(77L); return 1; })
                .when(messageMapper).insert(any(Message.class));
        Conversation c = new Conversation();
        c.setId(5L);
        when(conversationMapper.selectOneById(5L)).thenReturn(c);

        service.post(5L, 1L, Map.of("action", "members_added", "targetIds", List.of(9L)),
                List.of(1L, 2L, 9L));

        // 写入的消息：content_type=4、sender=操作者、内容含 action
        ArgumentCaptor<Message> mCap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(mCap.capture());
        Message m = mCap.getValue();
        assertThat(m.getContentType()).isEqualTo(Message.TYPE_SYSTEM);
        assertThat(m.getSenderId()).isEqualTo(1L);
        assertThat(m.getRecalled()).isEqualTo((short) 0);
        assertThat(m.getContent()).contains("\"action\":\"members_added\"");

        // 回写会话 last_message
        ArgumentCaptor<Conversation> cCap = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).update(cCap.capture());
        assertThat(cCap.getValue().getLastMessageId()).isEqualTo(77L);

        // 无事务上下文 → 立即发事件，接收者透传
        ArgumentCaptor<NewMessageEvent> eCap = ArgumentCaptor.forClass(NewMessageEvent.class);
        verify(eventPublisher).publishNewMessage(eCap.capture());
        assertThat(eCap.getValue().getConversationId()).isEqualTo(5L);
        assertThat(eCap.getValue().getMemberUserIds()).containsExactly(1L, 2L, 9L);
        assertThat(eCap.getValue().getMessage().getContentType()).isEqualTo(Message.TYPE_SYSTEM);
    }

    @Test
    void post_withinTransaction_defersPublishUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            doAnswer(inv -> { ((Message) inv.getArgument(0)).setId(1L); return 1; })
                    .when(messageMapper).insert(any(Message.class));
            when(conversationMapper.selectOneById(any())).thenReturn(null);

            service.post(5L, 1L, Map.of("action", "member_left"), List.of(1L));

            // 提交前不发
            verifyNoInteractions(eventPublisher);

            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCommit();
            }
            verify(eventPublisher).publishNewMessage(any(NewMessageEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void post_toleratesMissingConversation() {
        doAnswer(inv -> { ((Message) inv.getArgument(0)).setId(1L); return 1; })
                .when(messageMapper).insert(any(Message.class));
        when(conversationMapper.selectOneById(5L)).thenReturn(null);

        service.post(5L, 1L, Map.of("action", "group_created"), List.of(1L));

        verify(conversationMapper, never()).update(any());
        verify(eventPublisher).publishNewMessage(any(NewMessageEvent.class));
    }
}
