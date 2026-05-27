package com.lynn.nook.auth.service;

import com.lynn.nook.auth.config.JwtProperties;
import com.lynn.nook.auth.dto.ChangePasswordRequest;
import com.lynn.nook.auth.dto.LoginRequest;
import com.lynn.nook.auth.dto.LoginResponse;
import com.lynn.nook.auth.dto.MeResponse;
import com.lynn.nook.auth.dto.RegisterRequest;
import com.lynn.nook.auth.entity.User;
import com.lynn.nook.auth.mapper.UserMapper;
import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-must-be-at-least-32-bytes-long-aaaaaaaa");
        jwtProperties.setExpireMinutes(60);
        authService = new AuthService(userMapper, passwordEncoder, redis, jwtProperties);
    }

    // -------- register --------

    @Test
    void register_rejectsDuplicateUsername() {
        when(userMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("secret123");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USERNAME_EXISTS.getCode());
    }

    @Test
    void register_persistsHashedPasswordAndReturnsId() {
        when(userMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("HASH");
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).setId(42L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("secret123");
        req.setNickname("Ally");

        Long id = authService.register(req);

        assertThat(id).isEqualTo(42L);
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(cap.capture());
        User saved = cap.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("HASH");
        assertThat(saved.getNickname()).isEqualTo("Ally");
        assertThat(saved.getStatus()).isEqualTo((short) 1);
    }

    @Test
    void register_defaultsNicknameToUsernameWhenAbsent() {
        when(userMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("H");
        doAnswer(inv -> { ((User) inv.getArgument(0)).setId(1L); return 1; })
                .when(userMapper).insert(any(User.class));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("bob");
        req.setPassword("secret123");

        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getNickname()).isEqualTo("bob");
    }

    // -------- login --------

    @Test
    void login_rejectsUnknownUser() {
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("x");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_NOT_FOUND.getCode());
    }

    @Test
    void login_rejectsDisabledUser() {
        User u = new User();
        u.setId(1L); u.setUsername("alice");
        u.setPasswordHash("HASH"); u.setStatus((short) 0);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(u);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice"); req.setPassword("x");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_DISABLED.getCode());
    }

    @Test
    void login_rejectsWrongPassword() {
        User u = new User();
        u.setId(1L); u.setUsername("alice");
        u.setPasswordHash("HASH"); u.setStatus((short) 1);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("x", "HASH")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice"); req.setPassword("x");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PASSWORD_INCORRECT.getCode());
    }

    @Test
    void login_issuesTokenAndStoresInRedis() {
        User u = new User();
        u.setId(7L); u.setUsername("alice"); u.setNickname("Ally");
        u.setPasswordHash("HASH"); u.setStatus((short) 1);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("secret", "HASH")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice"); req.setPassword("secret");

        LoginResponse resp = authService.login(req);

        assertThat(resp.getUserId()).isEqualTo(7L);
        assertThat(resp.getUsername()).isEqualTo("alice");
        assertThat(resp.getNickname()).isEqualTo("Ally");
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getExpireSeconds()).isEqualTo(60 * 60L);
        verify(valueOps).set(anyString(), eq("7"), any(Duration.class));
    }

    // -------- logout --------

    @Test
    void logout_deletesTokenInRedis() {
        authService.logout("abc");
        verify(redis).delete("nook:auth:token:abc");
    }

    // -------- me --------

    @Test
    void me_returnsUserView() {
        User u = new User();
        u.setId(1L); u.setUsername("alice"); u.setNickname("Ally");
        u.setEmail("a@b.c"); u.setStatus((short) 1);
        when(userMapper.selectOneById(1L)).thenReturn(u);

        MeResponse me = authService.me(1L);

        assertThat(me.getId()).isEqualTo(1L);
        assertThat(me.getUsername()).isEqualTo("alice");
        assertThat(me.getEmail()).isEqualTo("a@b.c");
    }

    @Test
    void me_throwsWhenUserMissing() {
        when(userMapper.selectOneById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> authService.me(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_NOT_FOUND.getCode());
    }

    // -------- changePassword --------

    @Test
    void changePassword_rejectsWrongOldPassword() {
        User u = new User(); u.setId(1L); u.setPasswordHash("OLD");
        when(userMapper.selectOneById(1L)).thenReturn(u);
        when(passwordEncoder.matches("wrong", "OLD")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("wrong"); req.setNewPassword("newpass1");

        assertThatThrownBy(() -> authService.changePassword(1L, "tk", req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PASSWORD_INCORRECT.getCode());
        verify(userMapper, never()).update(any(User.class));
    }

    @Test
    void changePassword_rejectsSameAsOld() {
        User u = new User(); u.setId(1L); u.setPasswordHash("OLD");
        when(userMapper.selectOneById(1L)).thenReturn(u);
        when(passwordEncoder.matches("old", "OLD")).thenReturn(true);
        when(passwordEncoder.matches("old", "OLD")).thenReturn(true); // newPassword 也匹配旧 hash

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old"); req.setNewPassword("old");

        assertThatThrownBy(() -> authService.changePassword(1L, "tk", req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.SAME_AS_OLD_PASSWORD.getCode());
    }

    @Test
    void changePassword_updatesHashAndRevokesToken() {
        User u = new User(); u.setId(1L); u.setPasswordHash("OLD"); u.setUsername("alice");
        when(userMapper.selectOneById(1L)).thenReturn(u);
        when(passwordEncoder.matches("old", "OLD")).thenReturn(true);
        when(passwordEncoder.matches("new", "OLD")).thenReturn(false);
        when(passwordEncoder.encode("new")).thenReturn("NEW_HASH");

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old"); req.setNewPassword("new");

        authService.changePassword(1L, "tk", req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("NEW_HASH");
        verify(redis).delete("nook:auth:token:tk");
    }
}
