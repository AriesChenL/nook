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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Set;

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
    private SetOperations<String, String> setOps;
    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-must-be-at-least-32-bytes-long-aaaaaaaa");
        jwtProperties.setExpireMinutes(60);
        authService = new AuthService(userMapper, passwordEncoder, redis, jwtProperties);
    }

    // -------- register --------

    @Test
    void register_rejectsDuplicateUsername() {
        when(userMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        RegisterRequest req = new RegisterRequest("alice", "secret123", null);

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

        RegisterRequest req = new RegisterRequest("alice", "secret123", "Ally");

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

        RegisterRequest req = new RegisterRequest("bob", "secret123", null);

        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(cap.capture());
        assertThat(cap.getValue().getNickname()).isEqualTo("bob");
    }

    // -------- login --------

    @Test
    void login_rejectsUnknownUser() {
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        LoginRequest req = new LoginRequest("ghost", "x");

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

        LoginRequest req = new LoginRequest("alice", "x");

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

        LoginRequest req = new LoginRequest("alice", "x");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PASSWORD_INCORRECT.getCode());
    }

    @Test
    void login_issuesTokenAndStoresInRedis() {
        User u = new User();
        u.setId(7L); u.setPublicId("pub-7"); u.setUsername("alice"); u.setNickname("Ally");
        u.setPasswordHash("HASH"); u.setStatus((short) 1);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("secret", "HASH")).thenReturn(true);

        LoginRequest req = new LoginRequest("alice", "secret");

        LoginResponse resp = authService.login(req);

        // 对外只暴露 public_id（脱敏）；Redis/JWT 内部仍用数字 id
        assertThat(resp.userId()).isEqualTo("pub-7");
        assertThat(resp.username()).isEqualTo("alice");
        assertThat(resp.nickname()).isEqualTo("Ally");
        assertThat(resp.token()).isNotBlank();
        assertThat(resp.expireSeconds()).isEqualTo(60 * 60L);
        verify(valueOps).set(anyString(), eq("7"), any(Duration.class));
        // 新 token 登记进该用户的 token 集合并对齐 TTL
        verify(setOps).add(eq("nook:auth:user-tokens:7"), eq(resp.token()));
        verify(redis).expire(eq("nook:auth:user-tokens:7"), any(Duration.class));
    }

    @Test
    void login_kicksPreviousSessions() {
        User u = new User();
        u.setId(7L); u.setUsername("alice"); u.setNickname("Ally");
        u.setPasswordHash("HASH"); u.setStatus((short) 1);
        when(userMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("secret", "HASH")).thenReturn(true);
        // 该用户此前在两个端登录过
        when(setOps.members("nook:auth:user-tokens:7")).thenReturn(Set.of("old-1", "old-2"));

        LoginRequest req = new LoginRequest("alice", "secret");

        authService.login(req);

        // 旧 token 全部失效（批量删除）+ 清空集合
        verify(redis).delete(argThat((java.util.Collection<String> c) ->
                c.contains("nook:auth:token:old-1") && c.contains("nook:auth:token:old-2")));
        verify(redis).delete("nook:auth:user-tokens:7");
    }

    // -------- logout --------

    @Test
    void logout_deletesTokenAndRemovesFromUserSet() {
        when(valueOps.get("nook:auth:token:abc")).thenReturn("7");

        authService.logout("abc");

        verify(redis).delete("nook:auth:token:abc");
        verify(setOps).remove("nook:auth:user-tokens:7", "abc");
    }

    @Test
    void logout_skipsSetRemovalWhenTokenUnknown() {
        when(valueOps.get("nook:auth:token:gone")).thenReturn(null);

        authService.logout("gone");

        verify(redis).delete("nook:auth:token:gone");
        verifyNoInteractions(setOps);
    }

    // -------- me --------

    @Test
    void me_returnsUserView() {
        User u = new User();
        u.setId(1L); u.setPublicId("pub-1"); u.setUsername("alice"); u.setNickname("Ally");
        u.setEmail("a@b.c"); u.setStatus((short) 1);
        when(userMapper.selectOneById(1L)).thenReturn(u);

        MeResponse me = authService.me(1L);

        // 对外 id 输出 public_id（脱敏）
        assertThat(me.id()).isEqualTo("pub-1");
        assertThat(me.username()).isEqualTo("alice");
        assertThat(me.email()).isEqualTo("a@b.c");
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

        ChangePasswordRequest req = new ChangePasswordRequest("wrong", "newpass1");

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

        ChangePasswordRequest req = new ChangePasswordRequest("old", "old");

        assertThatThrownBy(() -> authService.changePassword(1L, "tk", req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.SAME_AS_OLD_PASSWORD.getCode());
    }

    @Test
    void changePassword_updatesHashAndRevokesAllSessions() {
        User u = new User(); u.setId(1L); u.setPasswordHash("OLD"); u.setUsername("alice");
        when(userMapper.selectOneById(1L)).thenReturn(u);
        when(passwordEncoder.matches("old", "OLD")).thenReturn(true);
        when(passwordEncoder.matches("new", "OLD")).thenReturn(false);
        when(passwordEncoder.encode("new")).thenReturn("NEW_HASH");

        ChangePasswordRequest req = new ChangePasswordRequest("old", "new");

        authService.changePassword(1L, "tk", req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("NEW_HASH");
        // 踢所有端：清空该用户 token 集合 + 显式删除本端 token
        verify(redis).delete("nook:auth:user-tokens:1");
        verify(redis).delete("nook:auth:token:tk");
    }
}
