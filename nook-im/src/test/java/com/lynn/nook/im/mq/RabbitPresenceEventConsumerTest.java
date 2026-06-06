package com.lynn.nook.im.mq;

import com.lynn.nook.im.ws.MessagePushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RabbitPresenceEventConsumerTest {

    private MessagePushService pushService;
    private RabbitPresenceEventConsumer consumer;

    @BeforeEach
    void setUp() {
        pushService = mock(MessagePushService.class);
        consumer = new RabbitPresenceEventConsumer(pushService);
    }

    @Test
    void onMessage_pushesPresenceToLocalFriends() {
        PresenceEvent event = PresenceEvent.builder()
                .userId(7L)
                .userPublicId("u7")
                .online(true)
                .friendUserIds(List.of(1L, 2L))
                .build();

        consumer.onMessage(event);

        verify(pushService).pushPresence(eq(List.of(1L, 2L)), eq("u7"), eq(true));
    }

    @Test
    void onMessage_skipsNulls() {
        consumer.onMessage(null);
        consumer.onMessage(PresenceEvent.builder().online(false).build());
        verifyNoInteractions(pushService);
    }
}
