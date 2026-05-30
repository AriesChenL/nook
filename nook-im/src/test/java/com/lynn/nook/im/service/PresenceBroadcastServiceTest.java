package com.lynn.nook.im.service;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.client.UserClient;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.PresenceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PresenceBroadcastServiceTest {

    private UserClient userClient;
    private MessageEventPublisher eventPublisher;
    private PresenceBroadcastService service;

    @BeforeEach
    void setUp() {
        userClient = mock(UserClient.class);
        eventPublisher = mock(MessageEventPublisher.class);
        service = new PresenceBroadcastService(userClient, eventPublisher);
    }

    @Test
    void onOnline_publishesPresenceToFriends() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of(1L, 2L, 3L)));

        service.onOnline(7L);

        ArgumentCaptor<PresenceEvent> cap = ArgumentCaptor.forClass(PresenceEvent.class);
        verify(eventPublisher).publishPresence(cap.capture());
        PresenceEvent e = cap.getValue();
        assertThat(e.getUserId()).isEqualTo(7L);
        assertThat(e.isOnline()).isTrue();
        assertThat(e.getFriendUserIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void onOffline_publishesOfflinePresence() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of(1L)));

        service.onOffline(7L);

        ArgumentCaptor<PresenceEvent> cap = ArgumentCaptor.forClass(PresenceEvent.class);
        verify(eventPublisher).publishPresence(cap.capture());
        assertThat(cap.getValue().isOnline()).isFalse();
    }

    @Test
    void broadcast_skippedWhenNoFriends() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of()));

        service.onOnline(7L);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void broadcast_degradesWhenUserServiceFails() {
        when(userClient.friendIds(anyLong())).thenThrow(new RuntimeException("nook-user down"));

        service.onOnline(7L); // 不抛异常

        verifyNoInteractions(eventPublisher);
    }
}
