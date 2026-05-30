package com.lynn.nook.im.service;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.client.UserClient;
import com.lynn.nook.im.mq.MessageEventPublisher;
import com.lynn.nook.im.mq.PresenceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在线状态广播：用户首次连接（上线）/ 最后一个连接断开（下线）时，
 * 取其好友列表并通过事件流通知好友。多实例下经 MQ broadcasting，好友连在哪个实例都能收到。
 * <p>取好友列表跨调 nook-user，失败时静默跳过——在线状态是锦上添花，不该影响连接建立/关闭。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceBroadcastService {

    private final UserClient userClient;
    private final MessageEventPublisher eventPublisher;

    public void onOnline(Long userId) {
        broadcast(userId, true);
    }

    public void onOffline(Long userId) {
        broadcast(userId, false);
    }

    private void broadcast(Long userId, boolean online) {
        List<Long> friendIds = fetchFriendIds(userId);
        if (friendIds.isEmpty()) return;
        eventPublisher.publishPresence(PresenceEvent.builder()
                .userId(userId)
                .online(online)
                .friendUserIds(friendIds)
                .build());
    }

    private List<Long> fetchFriendIds(Long userId) {
        try {
            Result<List<Long>> resp = userClient.friendIds(userId);
            if (resp == null || resp.getData() == null) return List.of();
            return resp.getData();
        } catch (Exception e) {
            log.warn("取好友列表失败，跳过在线状态广播：user={}, err={}", userId, e.getMessage());
            return List.of();
        }
    }
}
