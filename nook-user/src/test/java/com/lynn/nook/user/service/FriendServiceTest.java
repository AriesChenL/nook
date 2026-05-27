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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class FriendServiceTest {

    private FriendRequestMapper requestMapper;
    private FriendshipMapper friendshipMapper;
    private UserAccountMapper userMapper;
    private FriendService friendService;

    @BeforeEach
    void setUp() {
        requestMapper = mock(FriendRequestMapper.class);
        friendshipMapper = mock(FriendshipMapper.class);
        userMapper = mock(UserAccountMapper.class);
        friendService = new FriendService(requestMapper, friendshipMapper, userMapper);
    }

    private UserAccount user(Long id) {
        UserAccount u = new UserAccount();
        u.setId(id); u.setUsername("u" + id); u.setStatus((short) 1);
        return u;
    }

    // -------- sendRequest --------

    @Test
    void sendRequest_rejectsSelf() {
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId(1L);
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_CANNOT_ADD_SELF.getCode());
    }

    @Test
    void sendRequest_rejectsWhenTargetMissing() {
        when(userMapper.selectOneById(2L)).thenReturn(null);
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId(2L);
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_NOT_FOUND.getCode());
    }

    @Test
    void sendRequest_rejectsWhenAlreadyFriends() {
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId(2L);
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_ALREADY.getCode());
    }

    @Test
    void sendRequest_rejectsWhenPendingExists() {
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(requestMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId(2L);
        assertThatThrownBy(() -> friendService.sendRequest(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_EXISTS.getCode());
    }

    @Test
    void sendRequest_persistsPendingAndReturnsVO() {
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(requestMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectListByIds(any())).thenReturn(List.of(user(1L), user(2L)));
        doAnswer(inv -> { ((FriendRequest) inv.getArgument(0)).setId(99L); return 1; })
                .when(requestMapper).insert(any(FriendRequest.class));

        CreateFriendRequest req = new CreateFriendRequest();
        req.setToUserId(2L);
        req.setMessage("hi");

        var vo = friendService.sendRequest(1L, req);

        ArgumentCaptor<FriendRequest> cap = ArgumentCaptor.forClass(FriendRequest.class);
        verify(requestMapper).insert(cap.capture());
        FriendRequest saved = cap.getValue();
        assertThat(saved.getFromUserId()).isEqualTo(1L);
        assertThat(saved.getToUserId()).isEqualTo(2L);
        assertThat(saved.getStatus()).isEqualTo(FriendRequest.STATUS_PENDING);
        assertThat(vo.getId()).isEqualTo(99L);
        assertThat(vo.getMessage()).isEqualTo("hi");
    }

    // -------- accept / reject --------

    @Test
    void accept_rejectsWhenRequestMissingOrNotPending() {
        when(requestMapper.selectOneById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> friendService.accept(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_NOT_FOUND.getCode());

        FriendRequest done = new FriendRequest();
        done.setId(9L); done.setToUserId(1L);
        done.setStatus(FriendRequest.STATUS_ACCEPTED);
        when(requestMapper.selectOneById(9L)).thenReturn(done);
        assertThatThrownBy(() -> friendService.accept(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_NOT_FOUND.getCode());
    }

    @Test
    void accept_rejectsWhenCallerIsNotReceiver() {
        FriendRequest r = new FriendRequest();
        r.setId(9L); r.setFromUserId(2L); r.setToUserId(3L);
        r.setStatus(FriendRequest.STATUS_PENDING);
        when(requestMapper.selectOneById(9L)).thenReturn(r);

        assertThatThrownBy(() -> friendService.accept(99L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_REQUEST_FORBIDDEN.getCode());
    }

    @Test
    void accept_marksRequestAndInsertsDualFriendship() {
        FriendRequest r = new FriendRequest();
        r.setId(9L); r.setFromUserId(2L); r.setToUserId(3L);
        r.setStatus(FriendRequest.STATUS_PENDING);
        when(requestMapper.selectOneById(9L)).thenReturn(r);
        when(friendshipMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);

        friendService.accept(3L, 9L);

        verify(requestMapper).update(argThat(req ->
                req.getStatus().equals(FriendRequest.STATUS_ACCEPTED)));
        // 双向各一条
        verify(friendshipMapper, times(2)).insert(any(Friendship.class));
    }

    @Test
    void reject_marksRejected_doesNotCreateFriendship() {
        FriendRequest r = new FriendRequest();
        r.setId(9L); r.setFromUserId(2L); r.setToUserId(3L);
        r.setStatus(FriendRequest.STATUS_PENDING);
        when(requestMapper.selectOneById(9L)).thenReturn(r);

        friendService.reject(3L, 9L);

        verify(requestMapper).update(argThat(req ->
                req.getStatus().equals(FriendRequest.STATUS_REJECTED)));
        verify(friendshipMapper, never()).insert(any(Friendship.class));
    }

    // -------- removeFriend / updateRemark --------

    @Test
    void removeFriend_ignoresSelf() {
        friendService.removeFriend(1L, 1L);
        verifyNoInteractions(friendshipMapper);
    }

    @Test
    void removeFriend_deletesBothDirections() {
        friendService.removeFriend(1L, 2L);
        verify(friendshipMapper).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    void updateRemark_rejectsWhenNotFriend() {
        when(friendshipMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> friendService.updateRemark(1L, 2L, "buddy"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FRIEND_NOT_FOUND.getCode());
    }

    @Test
    void updateRemark_persistsRemark() {
        Friendship f = new Friendship();
        f.setId(1L); f.setOwnerId(1L); f.setFriendId(2L);
        when(friendshipMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(f);

        friendService.updateRemark(1L, 2L, "buddy");

        ArgumentCaptor<Friendship> cap = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipMapper).update(cap.capture());
        assertThat(cap.getValue().getRemark()).isEqualTo("buddy");
    }
}
