package com.lynn.nook.im.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceEventTest {

    @Test
    void roundTripsThroughJackson() throws Exception {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();
        PresenceEvent original = PresenceEvent.builder()
                .userId(7L)
                .online(true)
                .friendUserIds(List.of(1L, 2L, 3L))
                .build();

        String json = om.writeValueAsString(original);
        PresenceEvent decoded = om.readValue(json, PresenceEvent.class);

        assertThat(decoded.getUserId()).isEqualTo(7L);
        assertThat(decoded.isOnline()).isTrue();
        assertThat(decoded.getFriendUserIds()).containsExactly(1L, 2L, 3L);
    }
}
