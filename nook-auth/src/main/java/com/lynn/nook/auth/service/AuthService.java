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

        long expireMillis = Duration.ofMinutes(jwtProperties.getExpireMinutes()).toMillis();
        String token = JwtUtil.issue(
                jwtProperties.getSecret(),
                expireMillis,
                String.valueOf(u.getId()),
                Map.of("username", u.getUsername())
        );

        redis.opsForValue().set(
                CacheKeys.token(token),
                String.valueOf(u.getId()),
                Duration.ofMillis(expireMillis)
        );

        return new LoginResponse(
                u.getId(), u.getUsername(), u.getNickname(), token, expireMillis / 1000
        );
    }

    public void logout(String token) {
        redis.delete(CacheKeys.token(token));
    }

    public MeResponse me(Long userId) {
        User u = userMapper.selectOneById(userId);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return MeResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nickname(u.getNickname())
                .avatarUrl(u.getAvatarUrl())
                .email(u.getEmail())
                .phone(u.getPhone())
                .status(u.getStatus())
                .build();
    }

    /**
     * 改密码后会清除所有现存 token（强制重新登录），调用方负责清本地缓存。
     * 此处只清除当前请求带的 token；若需踢所有端，需要单独的 token-by-user 索引（后续可加）。
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
        if (currentToken != null && !currentToken.isBlank()) {
            redis.delete(CacheKeys.token(currentToken));
        }
        log.info("password changed: userId={}", userId);
    }
}
