package com.lynn.nook.im.service;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.client.UserClient;
import com.lynn.nook.im.ws.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 在线好友初始快照：只放行「好友」且「此刻 Redis 里在线」的。 */
class PresenceQueryServiceTest {

    private UserClient userClient;
    private PresenceService presenceService;
    private IdResolver idResolver;
    private PresenceQueryService service;

    @BeforeEach
    void setUp() {
        userClient = mock(UserClient.class);
        presenceService = mock(PresenceService.class);
        idResolver = mock(IdResolver.class);
        service = new PresenceQueryService(userClient, presenceService, idResolver);
        lenient().when(idResolver.userPublicIds(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            Map<Long, String> map = new HashMap<>();
            if (ids != null) for (Long id : ids) map.put(id, "u" + id);
            return map;
        });
    }

    @Test
    void onlineFriendIds_keepsOnlyOnlineFriends() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of(1L, 2L, 3L)));
        when(presenceService.isOnline(1L)).thenReturn(true);
        when(presenceService.isOnline(2L)).thenReturn(false);
        when(presenceService.isOnline(3L)).thenReturn(true);

        assertThat(service.onlineFriendIds(7L)).containsExactly(1L, 3L);
    }

    @Test
    void onlineFriendPublicIds_masksToPublicIds() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of(1L, 2L)));
        when(presenceService.isOnline(1L)).thenReturn(true);
        when(presenceService.isOnline(2L)).thenReturn(true);

        assertThat(service.onlineFriendPublicIds(7L)).containsExactly("u1", "u2");
    }

    @Test
    void onlineFriendPublicIds_dropsUnresolvableIds() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of(1L, 2L)));
        when(presenceService.isOnline(anyLong())).thenReturn(true);
        when(idResolver.userPublicIds(any())).thenReturn(Map.of(1L, "u1")); // 2L 解析不到

        assertThat(service.onlineFriendPublicIds(7L)).containsExactly("u1");
    }

    @Test
    void nullUser_returnsEmpty() {
        assertThat(service.onlineFriendIds(null)).isEmpty();
        verifyNoInteractions(userClient, presenceService);
    }

    @Test
    void noFriends_returnsEmptyWithoutTouchingRedis() {
        when(userClient.friendIds(7L)).thenReturn(Result.ok(List.of()));

        assertThat(service.onlineFriendIds(7L)).isEmpty();
        verify(presenceService, never()).isOnline(anyLong());
    }

    @Test
    void friendServiceFailure_returnsEmptySnapshot() {
        when(userClient.friendIds(7L)).thenThrow(new RuntimeException("nook-user down"));

        assertThat(service.onlineFriendIds(7L)).isEmpty();
        assertThat(service.onlineFriendPublicIds(7L)).isEmpty();
    }
}
