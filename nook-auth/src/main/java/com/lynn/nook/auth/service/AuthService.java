package com.lynn.nook.auth.service;

import com.lynn.nook.auth.config.JwtProperties;
import com.lynn.nook.auth.dto.ChangePasswordRequest;
import com.lynn.nook.auth.dto.LoginRequest;
import com.lynn.nook.auth.dto.LoginResponse;
import com.lynn.nook.auth.dto.MeResponse;
import com.lynn.nook.auth.dto.RegisterRequest;
import com.lynn.nook.auth.entity.User;
import com.lynn.nook.auth.mapper.UserMapper;
import com.lynn.nook.common.constant.CacheKeys;
import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.common.security.JwtUtil;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;

    public Long register(RegisterRequest req) {
        QueryWrapper qw = QueryWrapper.create().where("username = ?", req.getUsername());
        if (userMapper.selectCountByQuery(qw) > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        User u = new User();
        u.setPublicId(java.util.UUID.randomUUID().toString());
        u.setUsername(req.getUsername());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        u.setStatus((short) 1);
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        userMapper.insert(u);
        log.info("user registered: id={}, username={}", u.getId(), u.getUsername());
        return u.getId();
    }

    public LoginResponse login(LoginRequest req) {
        QueryWrapper qw = QueryWrapper.create().where("username = ?", req.getUsername());
        User u = userMapper.selectOneByQuery(qw);
        if (u == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }

        // 多端踢出：单端在线策略——新登录使该用户其它端的 token 立即失效
        revokeAllTokens(u.getId());

        long expireMillis = Duration.ofMinutes(jwtProperties.getExpireMinutes()).toMillis();
        String token = JwtUtil.issue(
                jwtProperties.getSecret(),
                expireMillis,
                String.valueOf(u.getId()),
                Map.of("username", u.getUsername())
        );

        Duration ttl = Duration.ofMillis(expireMillis);
        redis.opsForValue().set(CacheKeys.token(token), String.valueOf(u.getId()), ttl);
        registerToken(u.getId(), token, ttl);

        // 对外只暴露 public_id（脱敏）；JWT 主体内部仍用数字 id。
        return new LoginResponse(
                u.getPublicId(), u.getUsername(), u.getNickname(), token, expireMillis / 1000
        );
    }

    public void logout(String token) {
        // 先取 userId 以便从该用户的 token 集合里摘除，再删 token 本身
        String userId = redis.opsForValue().get(CacheKeys.token(token));
        redis.delete(CacheKeys.token(token));
        if (userId != null && !userId.isBlank()) {
            redis.opsForSet().remove(CacheKeys.userTokens(Long.parseLong(userId)), token);
        }
    }

    /** 把新 token 登记进该用户的有效 token 集合，并对齐 TTL。 */
    private void registerToken(Long userId, String token, Duration ttl) {
        String setKey = CacheKeys.userTokens(userId);
        redis.opsForSet().add(setKey, token);
        redis.expire(setKey, ttl);
    }

    /** 撤销该用户所有现存 token（踢所有端）：删除每个 token 键并清空集合。 */
    private void revokeAllTokens(Long userId) {
        String setKey = CacheKeys.userTokens(userId);
        Set<String> tokens = redis.opsForSet().members(setKey);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(CacheKeys::token).toList());
        }
        redis.delete(setKey);
    }

    public MeResponse me(Long userId) {
        User u = userMapper.selectOneById(userId);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return MeResponse.builder()
                .id(u.getPublicId())
                .username(u.getUsername())
                .nickname(u.getNickname())
                .avatarUrl(u.getAvatarUrl())
                .email(u.getEmail())
                .phone(u.getPhone())
                .status(u.getStatus())
                .build();
    }

    /**
     * 改密后强制所有端重新登录：撤销该用户全部现存 token（含本端）。调用方负责清本地缓存。
     */
    public void changePassword(Long userId, String currentToken, ChangePasswordRequest req) {
        User u = userMapper.selectOneById(userId);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(req.getOldPassword(), u.getPasswordHash())) {
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(req.getNewPassword(), u.getPasswordHash())) {
            throw new BusinessException(ResultCode.SAME_AS_OLD_PASSWORD);
        }
        u.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        u.setUpdatedAt(OffsetDateTime.now());
        userMapper.update(u);

        revokeAllTokens(userId);
        // 集合可能未含当前 token（边界场景），显式再删一次确保本端立即失效
        if (currentToken != null && !currentToken.isBlank()) {
            redis.delete(CacheKeys.token(currentToken));
        }
        log.info("password changed, all sessions revoked: userId={}", userId);
    }
}
