package com.lynn.nook.im.service;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.client.UserClient;
import com.lynn.nook.im.dto.MemberVO;
import com.lynn.nook.im.dto.UserBriefVO;
import com.lynn.nook.im.entity.ConversationMember;
import com.lynn.nook.im.mapper.ConversationMemberMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 群成员资料聚合：会话成员关系（role/joinedAt）+ 跨 nook-user 取到的公开资料（昵称/头像）。
 * nook-user 不可用时优雅降级，仅返回 userId + role，昵称/头像为 null，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final ConversationService conversationService;
    private final ConversationMemberMapper memberMapper;
    private final UserClient userClient;

    /** 列出会话成员（含资料）。调用方必须是该会话成员。 */
    public List<MemberVO> listMembers(Long currentUserId, Long conversationId) {
        conversationService.requireMember(conversationId, currentUserId);

        List<ConversationMember> rows = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id = ?", conversationId)
                .orderBy("role desc, joined_at asc"));
        if (rows.isEmpty()) return List.of();

        List<Long> userIds = rows.stream().map(ConversationMember::getUserId).toList();
        Map<Long, UserBriefVO> profiles = fetchProfiles(userIds);

        return rows.stream().map(m -> {
            UserBriefVO p = profiles.get(m.getUserId());
            return MemberVO.builder()
                    .userId(m.getUserId())
                    .role(m.getRole())
                    .joinedAt(m.getJoinedAt())
                    .username(p == null ? null : p.getUsername())
                    .nickname(p == null ? null : p.getNickname())
                    .avatarUrl(p == null ? null : p.getAvatarUrl())
                    .build();
        }).toList();
    }

    /** 批量取资料；任何失败都降级为空 map，让上层只返回 id。 */
    private Map<Long, UserBriefVO> fetchProfiles(List<Long> userIds) {
        try {
            Result<List<UserBriefVO>> resp = userClient.listByIds(userIds);
            if (resp == null || resp.getData() == null) return Map.of();
            return resp.getData().stream()
                    .filter(u -> u.getId() != null)
                    .collect(Collectors.toMap(UserBriefVO::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("聚合群成员资料失败，降级为仅返回 id：{}", e.getMessage());
            return Map.of();
        }
    }
}
