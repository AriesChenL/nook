package com.lynn.nook.im.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecallEventTest {

    @Test
    void roundTripsThroughJackson() throws Exception {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();
        RecallEvent original = RecallEvent.builder()
                .conversationId(10L)
                .messageId(99L)
                .memberUserIds(List.of(1L, 2L, 3L))
                .build();

        String json = om.writeValueAsString(original);
        RecallEvent decoded = om.readValue(json, RecallEvent.class);

        assertThat(decoded.getConversationId()).isEqualTo(10L);
        assertThat(decoded.getMessageId()).isEqualTo(99L);
        assertThat(decoded.getMemberUserIds()).containsExactly(1L, 2L, 3L);
    }
}
