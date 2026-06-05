package com.lynn.nook.im.mq;

import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.ws.MessagePushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalMessageEventPublisherTest {

    private MessagePushService pushService;
    private LocalMessageEventPublisher publisher;

    @BeforeEach
    void setUp() {
        pushService = mock(MessagePushService.class);
        publisher = new LocalMessageEventPublisher(pushService);
    }

    @Test
    void publish_delegatesToPushService() {
        MessageVO msg = MessageVO.builder().id("m1").build();
        NewMessageEvent event = NewMessageEvent.builder()
                .conversationId(10L)
                .memberUserIds(List.of(1L, 2L))
                .message(msg)
                .build();

        publisher.publishNewMessage(event);

        verify(pushService).pushNewMessage(eq(List.of(1L, 2L)), eq(msg));
    }

    @Test
    void publish_ignoresNullEventAndNullMessage() {
        publisher.publishNewMessage(null);
        publisher.publishNewMessage(NewMessageEvent.builder().conversationId(1L).build());

        verifyNoInteractions(pushService);
    }

    @Test
    void publishRecall_delegatesToPushService() {
        RecallEvent event = RecallEvent.builder()
                .conversationId(10L)
                .messageId(99L)
                .conversationPublicId("c10")
                .messagePublicId("m99")
                .memberUserIds(List.of(1L, 2L))
                .build();

        publisher.publishRecall(event);

        verify(pushService).pushRecall(eq(List.of(1L, 2L)), eq("c10"), eq("m99"));
    }

    @Test
    void publishRecall_ignoresNullEventAndNullMessageId() {
        publisher.publishRecall(null);
        publisher.publishRecall(RecallEvent.builder().conversationId(1L).build());

        verifyNoInteractions(pushService);
    }

    @Test
    void publishPresence_delegatesToPushService() {
        PresenceEvent event = PresenceEvent.builder()
                .userId(7L)
                .userPublicId("u7")
                .online(true)
                .friendUserIds(List.of(1L, 2L))
                .build();

        publisher.publishPresence(event);

        verify(pushService).pushPresence(eq(List.of(1L, 2L)), eq("u7"), eq(true));
    }

    @Test
    void publishPresence_ignoresNullEventAndNullUser() {
        publisher.publishPresence(null);
        publisher.publishPresence(PresenceEvent.builder().online(true).build());

        verifyNoInteractions(pushService);
    }
}
