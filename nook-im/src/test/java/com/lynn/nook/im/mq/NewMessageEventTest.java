package com.lynn.nook.im.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynn.nook.im.dto.MessageVO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewMessageEventTest {

    @Test
    void roundTripsThroughJackson() throws Exception {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();
        NewMessageEvent original = NewMessageEvent.builder()
                .conversationId(10L)
                .memberUserIds(List.of(1L, 2L, 3L))
                .message(MessageVO.builder()
                        .id(99L).conversationId(10L).senderId(1L)
                        .contentType((short) 1).content("hello")
                        .createdAt(OffsetDateTime.parse("2026-05-27T10:00:00+08:00"))
                        .build())
                .build();

        String json = om.writeValueAsString(original);
        NewMessageEvent decoded = om.readValue(json, NewMessageEvent.class);

        assertThat(decoded.getConversationId()).isEqualTo(10L);
        assertThat(decoded.getMemberUserIds()).containsExactly(1L, 2L, 3L);
        assertThat(decoded.getMessage().getId()).isEqualTo(99L);
        assertThat(decoded.getMessage().getContent()).isEqualTo("hello");
        assertThat(decoded.getMessage().getCreatedAt()).isEqualTo(original.getMessage().getCreatedAt());
    }
}
