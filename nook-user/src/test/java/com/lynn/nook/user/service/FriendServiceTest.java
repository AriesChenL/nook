package com.lynn.nook.user.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.user.dto.CreateFriendRequest;
import com.lynn.nook.user.entity.FriendRequest;
import com.lynn.nook.user.entity.Friendship;
import com.lynn.nook.user.entity.UserAccount;
import com.lynn.nook.user.mapper.FriendRequestMapper;
import com.lynn.nook.user.mapper.FriendshipMapper;
import com.lynn.nook.user.mapper.UserAccountMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FriendServiceTest {

    private FriendRequestMapper requestMapper;
    private FriendshipMapper friendshipMapper;
    private UserAccountMapper userMapper;
    private UserService userService;
    private FriendService friendService;

    @BeforeEach
    void setUp() {
        requestMapper = mock(FriendRequestMapper.class);
        friendshipMapper = mock(FriendshipMapper.class);
        userMapper = mock(UserAccountMapper.class);
        userService = mock(UserService.class);
        friendService = new FriendService(requestMapper, friendshipMapper, userMapper, userService);
    }

    private UserAccount user(Long id) {
        UserAccount u = new UserAccount();
        u.setId(id); u.setPublicId("pub" + id); u.setUsername("u" + id); u.setStatus((short) 1);
        return u;
    }

    // -------- listFriendIds --------

    @Test
    void listFriendIds_mapsFriendIds() {
        Friendship f1 = new Friendship(); f1.setOwnerId(7L); f1.setFriendId(1L);
        Friendship f2 = new Friendship(); f2.setOwnerId(7L); f2.setFriendId(2L);
        when(friendshipMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(f1, f2));

        List<Long> ids = friendService.listFriendIds(7L);

        assertThat(ids).containsExactly(1L, 2L);
    }

    // -------- sendRequest --------

    @Test
    void sendRequest_rejectsSelf() {
        when(userService.resolveId("pub1")).thenReturn(1L);
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId("pub1");
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_CANNOT_ADD_SELF.getCode());
    }

    @Test
    void sendRequest_rejectsWhenTargetMissing() {
        when(userService.resolveId("pub2")).thenReturn(2L);
        when(userMapper.selectOneById(2L)).thenReturn(null);
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId("pub2");
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_NOT_FOUND.getCode());
    }

    @Test
    void sendRequest_rejectsWhenAlreadyFriends() {
        when(userService.resolveId("pub2")).thenReturn(2L);
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId("pub2");
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_ALREADY.getCode());
    }

    @Test
    void sendRequest_rejectsWhenPendingExists() {
        when(userService.resolveId("pub2")).thenReturn(2L);
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(requestMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId("pub2");
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_EXISTS.getCode());
    }

    @Test
    void sendRequest_persistsPendingAndReturnsVO() {
        when(userService.resolveId("pub2")).thenReturn(2L);
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(requestMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectListByIds(any())).thenReturn(List.of(user(1L), user(2L)));
        doAnswer(inv -> { ((FriendRequest) inv.getArgument(0)).setId(99L); return 1; })
                .when(requestMapper).insert(any(FriendRequest.class));

        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId("pub2");
        req.setMessage("hi");

        var vo = friendService.sendRequest(1L, req);

        ArgumentCaptor<FriendRequest> cap = ArgumentCaptor.forClass(FriendRequest.class);
        verify(requestMapper).insert(cap.capture());
        FriendRequest saved = cap.getValue();
        assertThat(saved.getFromUserId()).isEqualTo(1L);
        assertThat(saved.getToUserId()).isEqualTo(2L);
        assertThat(saved.getStatus()).isEqualTo(FriendRequest.STATUS_PENDING);
        assertThat(saved.getPublicId()).isNotBlank();
        // 对外 id 输出申请 public_id（脱敏）
        assertThat(vo.getId()).isEqualTo(saved.getPublicId());
        assertThat(vo.getMessage()).isEqualTo("hi");
    }

    // -------- accept / reject --------

    @Test
    void accept_rejectsWhenRequestMissingOrNotPending() {
        when(requestMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> friendService.accept(1L, "rpub"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_NOT_FOUND.getCode());

        FriendRequest done = new FriendRequest();
        done.setId(9L); done.setPublicId("rpub"); done.setToUserId(1L);
        done.setStatus(FriendRequest.STATUS_ACCEPTED);
        when(requestMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(done);
        assertThatThrownBy(() -> friendService.accept(1L, "rpub"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_NOT_FOUND.getCode());
    }

    @Test
    void accept_rejectsWhenCallerIsNotReceiver() {
        FriendRequest r = new FriendRequest();
        r.setId(9L); r.setPublicId("rpub"); r.setFromUserId(2L); r.setToUserId(3L);
        r.setStatus(FriendRequest.STATUS_PENDING);
        when(requestMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(r);

        assertThatThrownBy(() -> friendService.accept(99L, "rpub"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_FORBIDDEN.getCode());
    }

    @Test
    void accept_marksRequestAndInsertsDualFriendship() {
        FriendRequest r = new FriendRequest();
        r.setId(9L); r.setPublicId("rpub"); r.setFromUserId(2L); r.setToUserId(3L);
        r.setStatus(FriendRequest.STATUS_PENDING);
        when(requestMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(r);
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);

        friendService.accept(3L, "rpub");

        verify(requestMapper).update(argThat(req ->
                req.getStatus().equals(FriendRequest.STATUS_ACCEPTED)));
        // 双向各一条
        verify(friendshipMapper, times(2)).insert(any(Friendship.class));
    }

    @Test
    void reject_marksRejected_doesNotCreateFriendship() {
        FriendRequest r = new FriendRequest();
        r.setId(9L); r.setPublicId("rpub"); r.setFromUserId(2L); r.setToUserId(3L);
        r.setStatus(FriendRequest.STATUS_PENDING);
        when(requestMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(r);

        friendService.reject(3L, "rpub");

        verify(requestMapper).update(argThat(req ->
                req.getStatus().equals(FriendRequest.STATUS_REJECTED)));
        verify(friendshipMapper, never()).insert(any(Friendship.class));
    }

    // -------- removeFriend / updateRemark --------

    @Test
    void removeFriend_ignoresSelf() {
        when(userService.resolveId("pub1")).thenReturn(1L);
        friendService.removeFriend(1L, "pub1");
        verifyNoInteractions(friendshipMapper);
    }

    @Test
    void removeFriend_deletesBothDirections() {
        when(userService.resolveId("pub2")).thenReturn(2L);
        friendService.removeFriend(1L, "pub2");
        verify(friendshipMapper).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    void updateRemark_rejectsWhenNotFriend() {
        when(userService.resolveId("pub2")).thenReturn(2L);
        when(friendshipMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> friendService.updateRemark(1L, "pub2", "buddy"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_NOT_FOUND.getCode());
    }

    @Test
    void updateRemark_persistsRemark() {
        when(userService.resolveId("pub2")).thenReturn(2L);
        Friendship f = new Friendship();
        f.setId(1L); f.setOwnerId(1L); f.setFriendId(2L);
        when(friendshipMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(f);

        friendService.updateRemark(1L, "pub2", "buddy");

        ArgumentCaptor<Friendship> cap = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipMapper).update(cap.capture());
        assertThat(cap.getValue().getRemark()).isEqualTo("buddy");
    }
}
