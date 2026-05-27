package com.lynn.nook.im.ws;

import com.lynn.nook.common.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 在线状态：Redis key 用 INT 计数器记录用户当前活跃的 WS 连接数。
 * 同一用户多端登录时，最后一个连接断开才视为下线。
 * 设置 TTL 兜底防止崩溃后泄漏。
 */
@Component
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;

    public void markOnline(long userId) {
        String key = CacheKeys.online(userId);
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redis.expire(key, TTL);
        } else {
            redis.expire(key, TTL); // 刷新 TTL
        }
    }

    public void markOffline(long userId) {
        String key = CacheKeys.online(userId);
        Long n = redis.opsForValue().decrement(key);
        if (n == null || n <= 0) {
            redis.delete(key);
        }
    }

    public boolean isOnline(long userId) {
        String v = redis.opsForValue().get(CacheKeys.online(userId));
        if (v == null) return false;
        try {
            return Integer.parseInt(v) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
