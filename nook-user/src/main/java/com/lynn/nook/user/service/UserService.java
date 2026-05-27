package com.lynn.nook.user.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.user.dto.UpdateProfileRequest;
import com.lynn.nook.user.dto.UserVO;
import com.lynn.nook.user.entity.UserAccount;
import com.lynn.nook.user.mapper.UserAccountMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserAccountMapper userMapper;

    public UserVO getSelf(Long userId) {
        UserAccount u = userMapper.selectOneById(userId);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return UserVO.from(u);
    }

    public UserVO getById(Long id) {
        UserAccount u = userMapper.selectOneById(id);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return UserVO.fromPublic(u);
    }

    public UserVO updateSelf(Long userId, UpdateProfileRequest req) {
        UserAccount u = userMapper.selectOneById(userId);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);

        if (req.getNickname()  != null) u.setNickname(req.getNickname());
        if (req.getAvatarUrl() != null) u.setAvatarUrl(req.getAvatarUrl());
        if (req.getEmail()     != null) u.setEmail(req.getEmail());
        if (req.getPhone()     != null) u.setPhone(req.getPhone());
        u.setUpdatedAt(OffsetDateTime.now());

        userMapper.update(u);
        return UserVO.from(u);
    }

    public List<UserVO> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String like = "%" + keyword.trim() + "%";
        QueryWrapper qw = QueryWrapper.create()
                .where("status = 1")
                .and("(username like ? or nickname like ? or email like ? or phone like ?)",
                        like, like, like, like)
                .limit(Math.max(1, Math.min(limit, 50)));
        return userMapper.selectListByQuery(qw).stream()
                .map(UserVO::fromPublic)
                .toList();
    }
}
