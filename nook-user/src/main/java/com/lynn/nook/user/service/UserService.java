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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserAccountMapper userMapper;

    public UserVO getSelf(Long userId) {
        UserAccount u = userMapper.selectOneById(userId);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return UserVO.from(u);
    }

    /** 单个用户公开资料：入参为 public_id（脱敏），解析回数字主键。 */
    public UserVO getByPublicId(String publicId) {
        UserAccount u = loadByPublicId(publicId);
        return UserVO.fromPublic(u);
    }

    /** 解析单个 user public_id -> 数字主键 id，解析不到抛资源不存在。 */
    public Long resolveId(String publicId) {
        return loadByPublicId(publicId).getId();
    }

    /**
     * 批量解析 user public_id -> 数字主键 id（供 nook-im 调用）。
     * 只返回能解析到的条目；解析不到的 public_id 不出现在结果里（不抛异常，便于批量容错）。
     * 返回 Map 保持插入顺序，key=public_id，value=数字 id。
     */
    public Map<String, Long> resolveIds(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) return Map.of();
        List<String> distinct = publicIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct().limit(500).toList();
        if (distinct.isEmpty()) return Map.of();
        String placeholders = distinct.stream().map(x -> "?").collect(Collectors.joining(","));
        QueryWrapper qw = QueryWrapper.create()
                .select("id", "public_id")
                .where("public_id in (" + placeholders + ")", distinct.toArray());
        Map<String, Long> result = new LinkedHashMap<>();
        for (String pid : distinct) result.putIfAbsent(pid, null);
        for (UserAccount u : userMapper.selectListByQuery(qw)) {
            result.put(u.getPublicId(), u.getId());
        }
        // 去掉未解析到的 null 值条目
        result.values().removeIf(Objects::isNull);
        return result;
    }

    private UserAccount loadByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        QueryWrapper qw = QueryWrapper.create().where("public_id = ?", publicId);
        UserAccount u = userMapper.selectOneByQuery(qw);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return u;
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

    /** 按 id 批量取公开资料（脱敏）。供 nook-im 等内部服务聚合成员资料用，去重并限量。 */
    public List<UserVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().limit(200).toList();
        if (distinct.isEmpty()) return List.of();
        // 手动展开占位符：MyBatis-Flex 的 `in` 直接传 List 会被当作单个参数（见踩坑记录）
        String placeholders = distinct.stream().map(x -> "?").collect(Collectors.joining(","));
        QueryWrapper qw = QueryWrapper.create().where("id in (" + placeholders + ")", distinct.toArray());
        return userMapper.selectListByQuery(qw).stream()
                .map(UserVO::fromPublic)
                .toList();
    }

    /**
     * 按 public_id 批量取公开资料（脱敏）。供前端用 —— 前端手里只有 public_id，
     * 不能走 {@link #listByIds}（那是数字入参，给 im 内部用）。解析不到的 public_id 直接忽略。
     */
    public List<UserVO> listByPublicIds(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) return List.of();
        List<String> distinct = publicIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct().limit(200).toList();
        if (distinct.isEmpty()) return List.of();
        String placeholders = distinct.stream().map(x -> "?").collect(Collectors.joining(","));
        QueryWrapper qw = QueryWrapper.create().where("public_id in (" + placeholders + ")", distinct.toArray());
        return userMapper.selectListByQuery(qw).stream()
                .map(UserVO::fromPublic)
                .toList();
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
