package com.lynn.nook.im.mq;

import com.lynn.nook.im.ws.MessagePushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RocketMqRecallEventConsumerTest {

    private MessagePushService pushService;
    private RocketMqRecallEventConsumer consumer;

    @BeforeEach
    void setUp() {
        pushService = mock(MessagePushService.class);
        consumer = new RocketMqRecallEventConsumer(pushService);
    }

    @Test
    void onMessage_pushesRecallToLocalMembers() {
        RecallEvent event = RecallEvent.builder()
                .conversationId(7L)
                .messageId(42L)
                .conversationPublicId("c7")
                .messagePublicId("m42")
                .memberUserIds(List.of(100L, 200L))
                .build();

        consumer.onMessage(event);

        verify(pushService).pushRecall(eq(List.of(100L, 200L)), eq("c7"), eq("m42"));
    }

    @Test
    void onMessage_skipsNulls() {
        consumer.onMessage(null);
        consumer.onMessage(RecallEvent.builder().conversationId(1L).build());
        verifyNoInteractions(pushService);
    }
}
