package com.lynn.nook.user.service;

import com.lynn.nook.user.dto.UserVO;
import com.lynn.nook.user.entity.UserAccount;
import com.lynn.nook.user.mapper.UserAccountMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 仅覆盖 listByIds（批量资料聚合入口）。其余资料/搜索逻辑较直白，暂不展开。 */
class UserServiceTest {

    private UserAccountMapper userMapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserAccountMapper.class);
        service = new UserService(userMapper);
    }

    private UserAccount account(Long id, String username) {
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(username);
        u.setEmail(username + "@x.com");
        u.setPhone("13800000000");
        return u;
    }

    @Test
    void listByIds_returnsEmptyForNullOrEmpty() {
        assertThat(service.listByIds(null)).isEmpty();
        assertThat(service.listByIds(List.of())).isEmpty();
        verifyNoInteractions(userMapper);
    }

    @Test
    void listByIds_mapsPublicView() {
        when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                account(1L, "alice"), account(2L, "bob")));

        List<UserVO> result = service.listByIds(Arrays.asList(1L, 2L, 1L, null));

        // 查询只发起一次（去重 + 过滤 null 后批量查）
        verify(userMapper, times(1)).selectListByQuery(any(QueryWrapper.class));
        // 脱敏：fromPublic 不带 email/phone
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEmail()).isNull();
        assertThat(result.get(0).getPhone()).isNull();
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
    }
}
