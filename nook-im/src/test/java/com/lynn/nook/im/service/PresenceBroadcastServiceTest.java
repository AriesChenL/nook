package com.lynn.nook.im.service;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.client.UserClient;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.PresenceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PresenceBroadcastServiceTest {

    private UserClient userClient;
    private MessageEventPublisher eventPublisher;
    private IdResolver idResolver;
    private PresenceBroadcastService service;

    @BeforeEach
    void setUp() {
        userClient = mock(UserClient.class);
        eventPublisher = mock(MessageEventPublisher.class);
        idResolver = mock(IdResolver.class);
        service = new PresenceBroadcastService(userClient, eventPublisher, idResolver);
        // 默认：数字 userId → public_id "u<id>"
        lenient().when(idResolver.userPublicIds(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            java.util.Map<Long, String> map = new java.util.HashMap<>();
            if (ids != null) for (Long id : ids) map.put(id, "u" + id);
            return map;
        });
    }

    @Test
    void onOnline_publishesPresenceToFriends() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of(1L, 2L, 3L)));

        service.onOnline(7L);

        ArgumentCaptor<PresenceEvent> cap = ArgumentCaptor.forClass(PresenceEvent.class);
        verify(eventPublisher).publishPresence(cap.capture());
        PresenceEvent e = cap.getValue();
        assertThat(e.getUserId()).isEqualTo(7L);
        assertThat(e.getUserPublicId()).isEqualTo("u7");
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
    void broadcast_skippedWhenPublicIdMissing() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of(1L)));
        when(idResolver.userPublicIds(any())).thenReturn(Map.of()); // 取不到自身 public_id

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
