package com.lynn.nook.im.service;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.client.UserClient;
import com.lynn.nook.im.ws.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在线状态查询：取当前用户的好友列表，过滤出此刻在线的（以 Redis 为准）。
 * <p>给前端登录/进页面时拉一次初始快照用——WS presence 帧只在跳变时推送，
 * 不会告诉刚连上的客户端「此刻谁在线」，缺这一步会导致已在线的好友一直显示离线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceQueryService {

    private final UserClient userClient;
    private final PresenceService presenceService;
    private final IdResolver idResolver;

    /** 在线好友的内部数字 userId（内部用，保持数字）。 */
    public List<Long> onlineFriendIds(Long userId) {
        if (userId == null) return List.of();
        List<Long> friendIds = fetchFriendIds(userId);
        if (friendIds.isEmpty()) return List.of();
        return friendIds.stream()
                .filter(id -> id != null && presenceService.isOnline(id))
                .toList();
    }

    /** 在线好友的 user public_id 列表（对外脱敏输出）。 */
    public List<String> onlineFriendPublicIds(Long userId) {
        List<Long> online = onlineFriendIds(userId);
        if (online.isEmpty()) return List.of();
        var pub = idResolver.userPublicIds(online);
        return online.stream().map(pub::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<Long> fetchFriendIds(Long userId) {
        try {
            Result<List<Long>> resp = userClient.friendIds(userId);
            if (resp == null || resp.getData() == null) return List.of();
            return resp.getData();
        } catch (Exception e) {
            log.warn("取好友列表失败，返回空在线快照：user={}, err={}", userId, e.getMessage());
            return List.of();
        }
    }
}
