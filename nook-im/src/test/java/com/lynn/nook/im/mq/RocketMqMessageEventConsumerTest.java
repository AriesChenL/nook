package com.lynn.nook.im.mq;

import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.ws.MessagePushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RocketMqMessageEventConsumerTest {

    private MessagePushService pushService;
    private RocketMqMessageEventConsumer consumer;

    @BeforeEach
    void setUp() {
        pushService = mock(MessagePushService.class);
        consumer = new RocketMqMessageEventConsumer(pushService);
    }

    @Test
    void onMessage_pushesToLocalMembers() {
        MessageVO msg = MessageVO.builder().id("m42").conversationId("c7").build();
        NewMessageEvent event = NewMessageEvent.builder()
                .conversationId(7L)
                .memberUserIds(List.of(100L, 200L))
                .message(msg)
                .build();

        consumer.onMessage(event);

        verify(pushService).pushNewMessage(eq(List.of(100L, 200L)), eq(msg));
    }

    @Test
    void onMessage_skipsNulls() {
        consumer.onMessage(null);
        consumer.onMessage(NewMessageEvent.builder().conversationId(1L).build());
        verifyNoInteractions(pushService);
    }
}
