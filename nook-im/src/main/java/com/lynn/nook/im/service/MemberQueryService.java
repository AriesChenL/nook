package com.lynn.nook.im.service;

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

/**
 * 群成员资料聚合：会话成员关系（role/joinedAt）+ 跨 nook-user 取到的公开资料（昵称/头像 + public_id）。
 * nook-user 不可用时优雅降级：userId（public_id）/昵称/头像可能为 null，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final ConversationService conversationService;
    private final ConversationMemberMapper memberMapper;
    private final IdResolver idResolver;

    /** 列出会话成员（含资料）。调用方必须是该会话成员。userId 输出 user public_id。 */
    public List<MemberVO> listMembers(Long currentUserId, Long conversationId) {
        conversationService.requireMember(conversationId, currentUserId);

        List<ConversationMember> rows = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id = ?", conversationId)
                .orderBy("role desc, joined_at asc"));
        if (rows.isEmpty()) return List.of();

        List<Long> userIds = rows.stream().map(ConversationMember::getUserId).toList();
        // 数字 userId → UserBriefVO(id=public_id, 昵称/头像)；nook-user 不可用时为空 map（降级）
        Map<Long, UserBriefVO> profiles = idResolver.userProfilesByNumericId(userIds);

        return rows.stream().map(m -> {
            UserBriefVO p = profiles.get(m.getUserId());
            return new MemberVO(
                    p == null ? null : p.id(),
                    m.getRole(),
                    m.getJoinedAt(),
                    p == null ? null : p.username(),
                    p == null ? null : p.nickname(),
                    p == null ? null : p.avatarUrl());
        }).toList();
    }
}
