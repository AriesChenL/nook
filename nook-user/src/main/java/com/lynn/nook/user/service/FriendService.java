package com.lynn.nook.user.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.user.dto.CreateFriendRequest;
import com.lynn.nook.user.dto.FriendRequestVO;
import com.lynn.nook.user.dto.FriendVO;
import com.lynn.nook.user.dto.UserVO;
import com.lynn.nook.user.entity.FriendRequest;
import com.lynn.nook.user.entity.Friendship;
import com.lynn.nook.user.entity.UserAccount;
import com.lynn.nook.user.mapper.FriendRequestMapper;
import com.lynn.nook.user.mapper.FriendshipMapper;
import com.lynn.nook.user.mapper.UserAccountMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRequestMapper requestMapper;
    private final FriendshipMapper friendshipMapper;
    private final UserAccountMapper userMapper;
    private final UserService userService;

    /** 发送好友申请。入参 toUserId 为目标用户 public_id（脱敏），入口解析回数字主键。 */
    @Transactional
    public FriendRequestVO sendRequest(Long fromUserId, CreateFriendRequest req) {
        Long toUserId = userService.resolveId(req.getToUserId());
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(ResultCode.FRIEND_CANNOT_ADD_SELF);
        }
        UserAccount target = userMapper.selectOneById(toUserId);
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);

        if (areFriends(fromUserId, toUserId)) {
            throw new BusinessException(ResultCode.FRIEND_ALREADY);
        }

        QueryWrapper qw = QueryWrapper.create()
                .where("from_user_id = ?", fromUserId)
                .and("to_user_id = ?", toUserId)
                .and("status = ?", FriendRequest.STATUS_PENDING);
        if (requestMapper.selectCountByQuery(qw) > 0) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_EXISTS);
        }

        FriendRequest r = new FriendRequest();
        r.setPublicId(java.util.UUID.randomUUID().toString());
        r.setFromUserId(fromUserId);
        r.setToUserId(toUserId);
        r.setMessage(req.getMessage());
        r.setStatus(FriendRequest.STATUS_PENDING);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        requestMapper.insert(r);

        return toVO(r);
    }

    /** 接受好友申请：仅接收方可操作。入参 requestPublicId 为申请 public_id（脱敏）。 */
    @Transactional
    public void accept(Long currentUserId, String requestPublicId) {
        FriendRequest r = loadPending(currentUserId, requestPublicId);
        r.setStatus(FriendRequest.STATUS_ACCEPTED);
        r.setUpdatedAt(OffsetDateTime.now());
        requestMapper.update(r);

        addFriendship(r.getFromUserId(), r.getToUserId());
        addFriendship(r.getToUserId(), r.getFromUserId());
    }

    /** 拒绝好友申请：仅接收方可操作。入参 requestPublicId 为申请 public_id（脱敏）。 */
    @Transactional
    public void reject(Long currentUserId, String requestPublicId) {
        FriendRequest r = loadPending(currentUserId, requestPublicId);
        r.setStatus(FriendRequest.STATUS_REJECTED);
        r.setUpdatedAt(OffsetDateTime.now());
        requestMapper.update(r);
    }

    /** 我收到的好友申请列表。 */
    public List<FriendRequestVO> incoming(Long userId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("to_user_id = ?", userId)
                .orderBy("created_at desc");
        return requestMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .toList();
    }

    /** 我发出的好友申请列表。 */
    public List<FriendRequestVO> outgoing(Long userId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("from_user_id = ?", userId)
                .orderBy("created_at desc");
        return requestMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .toList();
    }

    /** 删除好友：删两条 friendships 记录（双向）。幂等。入参 friendPublicId 为好友 public_id（脱敏）。 */
    @Transactional
    public void removeFriend(Long userId, String friendPublicId) {
        Long friendUserId = userService.resolveId(friendPublicId);
        if (userId.equals(friendUserId)) return;
        QueryWrapper qw = QueryWrapper.create()
                .where("(owner_id = ? and friend_id = ?) or (owner_id = ? and friend_id = ?)",
                        userId, friendUserId, friendUserId, userId);
        friendshipMapper.deleteByQuery(qw);
    }

    /** 修改好友备注。入参 friendPublicId 为好友 public_id（脱敏）。 */
    @Transactional
    public void updateRemark(Long userId, String friendPublicId, String remark) {
        Long friendUserId = userService.resolveId(friendPublicId);
        QueryWrapper qw = QueryWrapper.create()
                .where("owner_id = ?", userId).and("friend_id = ?", friendUserId);
        Friendship f = friendshipMapper.selectOneByQuery(qw);
        if (f == null) throw new BusinessException(ResultCode.FRIEND_NOT_FOUND);
        f.setRemark(remark);
        friendshipMapper.update(f);
    }

    /** 我的好友列表。 */
    public List<FriendVO> listFriends(Long userId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("owner_id = ?", userId)
                .orderBy("created_at desc");
        List<Friendship> rows = friendshipMapper.selectListByQuery(qw);
        if (rows.isEmpty()) return List.of();

        Map<Long, UserAccount> users = loadUsers(rows.stream().map(Friendship::getFriendId).toList());
        return rows.stream()
                .map(f -> FriendVO.builder()
                        .user(UserVO.fromPublic(users.get(f.getFriendId())))
                        .remark(f.getRemark())
                        .createdAt(f.getCreatedAt())
                        .build())
                .toList();
    }

    /** 某用户的好友 userId 列表（owner_id = userId 的 friend_id）。供 nook-im 在线状态广播用。 */
    public List<Long> listFriendIds(Long userId) {
        QueryWrapper qw = QueryWrapper.create().where("owner_id = ?", userId);
        return friendshipMapper.selectListByQuery(qw).stream()
                .map(Friendship::getFriendId)
                .toList();
    }

    // ---------- helpers ----------

    private FriendRequest loadPending(Long currentUserId, String requestPublicId) {
        if (requestPublicId == null || requestPublicId.isBlank()) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }
        QueryWrapper qw = QueryWrapper.create().where("public_id = ?", requestPublicId);
        FriendRequest r = requestMapper.selectOneByQuery(qw);
        if (r == null || r.getStatus() == null
                || r.getStatus() != FriendRequest.STATUS_PENDING) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!currentUserId.equals(r.getToUserId())) {
            throw new BusinessException(ResultCode.FRIEND_REQUEST_FORBIDDEN);
        }
        return r;
    }

    private boolean areFriends(Long a, Long b) {
        QueryWrapper qw = QueryWrapper.create()
                .where("owner_id = ?", a).and("friend_id = ?", b);
        return friendshipMapper.selectCountByQuery(qw) > 0;
    }

    private void addFriendship(Long ownerId, Long friendId) {
        if (areFriends(ownerId, friendId)) return;
        Friendship f = new Friendship();
        f.setOwnerId(ownerId);
        f.setFriendId(friendId);
        f.setCreatedAt(OffsetDateTime.now());
        friendshipMapper.insert(f);
    }

    private FriendRequestVO toVO(FriendRequest r) {
        Map<Long, UserAccount> users = loadUsers(List.of(r.getFromUserId(), r.getToUserId()));
        return FriendRequestVO.from(
                r,
                UserVO.fromPublic(users.get(r.getFromUserId())),
                UserVO.fromPublic(users.get(r.getToUserId()))
        );
    }

    private Map<Long, UserAccount> loadUsers(List<Long> ids) {
        Set<Long> uniq = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (uniq.isEmpty()) return Map.of();
        List<UserAccount> list = userMapper.selectListByIds(uniq);
        Map<Long, UserAccount> map = new HashMap<>(list.size());
        for (UserAccount u : list) map.put(u.getId(), u);
        return map;
    }
}
