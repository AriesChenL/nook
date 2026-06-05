package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.dto.ConversationVO;
import com.lynn.nook.im.dto.CreateGroupRequest;
import com.lynn.nook.im.dto.UpdateGroupRequest;
import com.lynn.nook.im.entity.Conversation;
import com.lynn.nook.im.entity.ConversationMember;
import com.lynn.nook.im.mapper.ConversationMapper;
import com.lynn.nook.im.mapper.ConversationMemberMapper;
import com.lynn.nook.im.mapper.MessageMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper memberMapper;
    private final MessageMapper messageMapper;
    private final SystemMessageService systemMessageService;
    private final IdResolver idResolver;

    /** 获取或创建单聊会话。 */
    @Transactional
    public ConversationVO getOrCreateDirect(Long currentUserId, Long peerUserId) {
        if (currentUserId.equals(peerUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        Long existing = findDirectConversationId(currentUserId, peerUserId);
        if (existing != null) {
            return buildVO(conversationMapper.selectOneById(existing), currentUserId);
        }

        Conversation c = new Conversation();
        c.setPublicId(UUID.randomUUID().toString());
        c.setType(Conversation.TYPE_DIRECT);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.insert(c);

        addMember(c.getId(), currentUserId, ConversationMember.ROLE_MEMBER);
        addMember(c.getId(), peerUserId,    ConversationMember.ROLE_MEMBER);

        return buildVO(c, currentUserId);
    }

    /** 我的会话列表（按最近消息时间倒序）。 */
    public List<ConversationVO> listMine(Long userId) {
        List<ConversationMember> myMembers = memberMapper.selectListByQuery(
                QueryWrapper.create().where("user_id = ?", userId));
        if (myMembers.isEmpty()) return List.of();

        List<Long> convIds = myMembers.stream().map(ConversationMember::getConversationId).toList();
        Map<Long, ConversationMember> myMemberByConv = myMembers.stream()
                .collect(Collectors.toMap(ConversationMember::getConversationId, m -> m));

        List<Conversation> convs = conversationMapper.selectListByIds(convIds);
        Map<Long, List<Long>> membersByConv = loadMemberIds(convIds);

        // 批量预解析：所有涉及的 user id（成员 + 群主）→ public_id，所有 lastMessageId → public_id
        Set<Long> allUserIds = new LinkedHashSet<>();
        Set<Long> allMsgIds = new LinkedHashSet<>();
        for (Conversation c : convs) {
            allUserIds.addAll(membersByConv.getOrDefault(c.getId(), List.of()));
            if (c.getOwnerId() != null) allUserIds.add(c.getOwnerId());
            if (c.getLastMessageId() != null) allMsgIds.add(c.getLastMessageId());
            ConversationMember mm = myMemberByConv.get(c.getId());
            if (mm != null && mm.getLastReadMsgId() != null && mm.getLastReadMsgId() > 0) {
                allMsgIds.add(mm.getLastReadMsgId());
            }
        }
        Map<Long, String> userPub = idResolver.userPublicIds(allUserIds);
        Map<Long, String> msgPub = messagePublicIds(allMsgIds);

        List<ConversationVO> result = new ArrayList<>(convs.size());
        for (Conversation c : convs) {
            ConversationVO vo = ConversationVO.from(c);
            List<Long> memberNumIds = membersByConv.getOrDefault(c.getId(), List.of());
            vo.setMemberIds(memberNumIds.stream().map(userPub::get).toList());
            vo.setOwnerId(c.getOwnerId() == null ? null : userPub.get(c.getOwnerId()));
            vo.setLastMessageId(c.getLastMessageId() == null ? null : msgPub.get(c.getLastMessageId()));
            ConversationMember mm = myMemberByConv.get(c.getId());
            Long lastRead = mm == null || mm.getLastReadMsgId() == null ? 0L : mm.getLastReadMsgId();
            vo.setLastReadMsgId(lastRead > 0 ? msgPub.get(lastRead) : null);
            vo.setMyRole(mm == null ? null : mm.getRole());
            vo.setUnreadCount(countUnread(c.getId(), userId, lastRead));
            result.add(vo);
        }
        result.sort(Comparator.comparing(
                (ConversationVO v) -> v.getLastMessageAt() == null ? OffsetDateTime.MIN : v.getLastMessageAt()
        ).reversed());
        return result;
    }

    public ConversationVO get(Long userId, Long conversationId) {
        Conversation c = conversationMapper.selectOneById(conversationId);
        if (c == null) throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        requireMember(conversationId, userId);
        return buildVO(c, userId);
    }

    /** 已读上报。 */
    public void updateReadCursor(Long userId, Long conversationId, Long lastReadMsgId) {
        ConversationMember m = requireMember(conversationId, userId);
        if (lastReadMsgId == null || lastReadMsgId < 0) return;
        if (m.getLastReadMsgId() != null && m.getLastReadMsgId() >= lastReadMsgId) return;
        m.setLastReadMsgId(lastReadMsgId);
        memberMapper.update(m);
    }

    // ==================== 群聊管理 ====================

    /** 创建群聊：创建者自动成为群主，初始成员去重并排除创建者自身。 */
    @Transactional
    public ConversationVO createGroup(Long ownerId, CreateGroupRequest req, List<Long> memberIds) {
        OffsetDateTime now = OffsetDateTime.now();
        Conversation c = new Conversation();
        c.setPublicId(UUID.randomUUID().toString());
        c.setType(Conversation.TYPE_GROUP);
        c.setName(req.getName());
        c.setAvatarUrl(req.getAvatarUrl());
        c.setOwnerId(ownerId);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        conversationMapper.insert(c);

        List<Long> invited = dedupExclude(memberIds, ownerId);
        addMember(c.getId(), ownerId, ConversationMember.ROLE_OWNER);
        for (Long uid : invited) {
            addMember(c.getId(), uid, ConversationMember.ROLE_MEMBER);
        }

        List<Long> all = new ArrayList<>();
        all.add(ownerId);
        all.addAll(invited);
        systemMessageService.post(c.getId(), ownerId,
                body("action", "group_created", "operatorId", ownerId), all);
        return buildVO(c, ownerId);
    }

    /** 邀请成员入群：管理员/群主可操作，已在群中的自动跳过。 */
    @Transactional
    public ConversationVO addMembers(Long operatorId, Long conversationId, List<Long> userIds) {
        Conversation c = requireGroup(conversationId);
        requireAdminOrOwner(conversationId, operatorId);

        Set<Long> existing = new LinkedHashSet<>(listMemberIds(conversationId));
        List<Long> added = new ArrayList<>();
        for (Long uid : dedupExclude(userIds, null)) {
            if (existing.contains(uid)) continue;
            addMember(conversationId, uid, ConversationMember.ROLE_MEMBER);
            added.add(uid);
        }
        touch(c);
        if (!added.isEmpty()) {
            systemMessageService.post(conversationId, operatorId,
                    body("action", "members_added", "operatorId", operatorId, "targetIds", added),
                    listMemberIds(conversationId));
        }
        return buildVO(c, operatorId);
    }

    /** 移除成员：群主可移除任何人（除自己）；管理员只能移除普通成员；不能移除群主。 */
    @Transactional
    public void removeMember(Long operatorId, Long conversationId, Long targetUserId) {
        requireGroup(conversationId);
        ConversationMember operator = requireAdminOrOwner(conversationId, operatorId);
        if (operatorId.equals(targetUserId)) {
            // 移除自己应走退群接口
            throw new BusinessException(ResultCode.GROUP_PERMISSION_DENIED);
        }
        ConversationMember target = findMember(conversationId, targetUserId);
        if (target == null) throw new BusinessException(ResultCode.GROUP_MEMBER_NOT_FOUND);
        if (target.getRole() == ConversationMember.ROLE_OWNER) {
            throw new BusinessException(ResultCode.GROUP_PERMISSION_DENIED);
        }
        // 管理员不能移除其他管理员，只有群主可以
        if (operator.getRole() != ConversationMember.ROLE_OWNER
                && target.getRole() == ConversationMember.ROLE_ADMIN) {
            throw new BusinessException(ResultCode.GROUP_PERMISSION_DENIED);
        }
        // 删除前 snapshot 全体成员（含被踢者），让被踢者本人也收到通知
        List<Long> recipients = listMemberIds(conversationId);
        memberMapper.deleteById(target.getId());
        systemMessageService.post(conversationId, operatorId,
                body("action", "member_removed", "operatorId", operatorId, "targetId", targetUserId),
                recipients);
    }

    /** 修改群名称/头像：管理员/群主可操作。 */
    @Transactional
    public ConversationVO updateGroup(Long operatorId, Long conversationId, UpdateGroupRequest req) {
        Conversation c = requireGroup(conversationId);
        requireAdminOrOwner(conversationId, operatorId);
        if (req.getName() != null && !req.getName().isBlank()) {
            c.setName(req.getName());
        }
        if (req.getAvatarUrl() != null) {
            c.setAvatarUrl(req.getAvatarUrl());
        }
        touch(c);
        return buildVO(c, operatorId);
    }

    /** 退群：任何成员可退；群主必须先转让群主。 */
    @Transactional
    public void leaveGroup(Long userId, Long conversationId) {
        requireGroup(conversationId);
        ConversationMember me = requireMember(conversationId, userId);
        if (me.getRole() == ConversationMember.ROLE_OWNER) {
            throw new BusinessException(ResultCode.GROUP_OWNER_CANNOT_LEAVE);
        }
        List<Long> recipients = listMemberIds(conversationId); // 含退群者本人，便于其多端同步
        memberMapper.deleteById(me.getId());
        systemMessageService.post(conversationId, userId,
                body("action", "member_left", "operatorId", userId), recipients);
    }

    /** 转让群主：仅群主可操作，目标必须是群成员；原群主降为普通成员。 */
    @Transactional
    public ConversationVO transferOwner(Long operatorId, Long conversationId, Long newOwnerId) {
        Conversation c = requireGroup(conversationId);
        ConversationMember owner = requireOwner(conversationId, operatorId);
        if (operatorId.equals(newOwnerId)) {
            return buildVO(c, operatorId);
        }
        ConversationMember target = findMember(conversationId, newOwnerId);
        if (target == null) throw new BusinessException(ResultCode.GROUP_MEMBER_NOT_FOUND);

        owner.setRole(ConversationMember.ROLE_MEMBER);
        memberMapper.update(owner);
        target.setRole(ConversationMember.ROLE_OWNER);
        memberMapper.update(target);

        c.setOwnerId(newOwnerId);
        touch(c);
        systemMessageService.post(conversationId, operatorId,
                body("action", "owner_transferred", "operatorId", operatorId, "targetId", newOwnerId),
                listMemberIds(conversationId));
        return buildVO(c, operatorId);
    }

    /** 设置成员角色（普通/管理员）：仅群主可操作；不能改群主自己，也不能设成群主。 */
    @Transactional
    public ConversationVO setMemberRole(Long operatorId, Long conversationId, Long targetUserId, Short role) {
        Conversation c = requireGroup(conversationId);
        requireOwner(conversationId, operatorId);
        if (role == null
                || (role != ConversationMember.ROLE_MEMBER && role != ConversationMember.ROLE_ADMIN)) {
            throw new BusinessException(ResultCode.GROUP_ROLE_INVALID);
        }
        ConversationMember target = findMember(conversationId, targetUserId);
        if (target == null) throw new BusinessException(ResultCode.GROUP_MEMBER_NOT_FOUND);
        if (target.getRole() == ConversationMember.ROLE_OWNER) {
            throw new BusinessException(ResultCode.GROUP_PERMISSION_DENIED);
        }
        target.setRole(role);
        memberMapper.update(target);
        systemMessageService.post(conversationId, operatorId,
                body("action", "role_changed", "operatorId", operatorId,
                        "targetId", targetUserId, "role", role),
                listMemberIds(conversationId));
        return buildVO(c, operatorId);
    }

    // ---------- 群聊 helpers ----------

    /** 构造系统消息内容体（保持插入顺序）。kv 形如 ("action","x","operatorId",1L)。 */
    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Conversation requireGroup(Long conversationId) {
        Conversation c = conversationMapper.selectOneById(conversationId);
        if (c == null) throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        if (c.getType() == null || c.getType() != Conversation.TYPE_GROUP) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_GROUP);
        }
        return c;
    }

    private ConversationMember requireAdminOrOwner(Long conversationId, Long userId) {
        ConversationMember m = requireMember(conversationId, userId);
        if (m.getRole() != ConversationMember.ROLE_ADMIN && m.getRole() != ConversationMember.ROLE_OWNER) {
            throw new BusinessException(ResultCode.GROUP_PERMISSION_DENIED);
        }
        return m;
    }

    private ConversationMember requireOwner(Long conversationId, Long userId) {
        ConversationMember m = requireMember(conversationId, userId);
        if (m.getRole() != ConversationMember.ROLE_OWNER) {
            throw new BusinessException(ResultCode.GROUP_PERMISSION_DENIED);
        }
        return m;
    }

    private ConversationMember findMember(Long conversationId, Long userId) {
        return memberMapper.selectOneByQuery(QueryWrapper.create()
                .where("conversation_id = ?", conversationId)
                .and("user_id = ?", userId));
    }

    /** 去重并按插入顺序保留；excludeId 非空时排除该 id。 */
    private List<Long> dedupExclude(List<Long> ids, Long excludeId) {
        if (ids == null || ids.isEmpty()) return List.of();
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) continue;
            if (excludeId != null && excludeId.equals(id)) continue;
            set.add(id);
        }
        return new ArrayList<>(set);
    }

    private void touch(Conversation c) {
        c.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.update(c);
    }

    /** 校验当前用户在该会话中，返回成员记录。 */
    public ConversationMember requireMember(Long conversationId, Long userId) {
        ConversationMember m = memberMapper.selectOneByQuery(QueryWrapper.create()
                .where("conversation_id = ?", conversationId)
                .and("user_id = ?", userId));
        if (m == null) throw new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER);
        return m;
    }

    // ---------- helpers ----------

    private Long findDirectConversationId(Long a, Long b) {
        QueryWrapper qw = QueryWrapper.create()
                .where("type = ?", Conversation.TYPE_DIRECT)
                .and("exists (select 1 from conversation_members m1 where m1.conversation_id = conversations.id and m1.user_id = ?)", a)
                .and("exists (select 1 from conversation_members m2 where m2.conversation_id = conversations.id and m2.user_id = ?)", b)
                .limit(1);
        Conversation found = conversationMapper.selectOneByQuery(qw);
        return found == null ? null : found.getId();
    }

    private void addMember(Long conversationId, Long userId, short role) {
        ConversationMember m = new ConversationMember();
        m.setConversationId(conversationId);
        m.setUserId(userId);
        m.setRole(role);
        m.setLastReadMsgId(0L);
        m.setMute((short) 0);
        m.setJoinedAt(OffsetDateTime.now());
        memberMapper.insert(m);
    }

    private Map<Long, List<Long>> loadMemberIds(List<Long> convIds) {
        if (convIds.isEmpty()) return Map.of();
        String placeholders = convIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<ConversationMember> rows = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id in (" + placeholders + ")", convIds.toArray()));
        Map<Long, List<Long>> map = new HashMap<>();
        for (ConversationMember m : rows) {
            map.computeIfAbsent(m.getConversationId(), k -> new ArrayList<>()).add(m.getUserId());
        }
        return map;
    }

    private long countUnread(Long conversationId, Long userId, Long lastReadMsgId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("conversation_id = ?", conversationId)
                .and("id > ?", lastReadMsgId == null ? 0L : lastReadMsgId)
                .and("sender_id <> ?", userId);
        return messageMapper.selectCountByQuery(qw);
    }

    private ConversationVO buildVO(Conversation c, Long currentUserId) {
        ConversationVO vo = ConversationVO.from(c);
        List<ConversationMember> ms = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id = ?", c.getId()));
        List<Long> memberNumIds = ms.stream().map(ConversationMember::getUserId).toList();

        // 数字 user id → public_id（含群主）
        Set<Long> userIds = new LinkedHashSet<>(memberNumIds);
        if (c.getOwnerId() != null) userIds.add(c.getOwnerId());
        Map<Long, String> userPub = idResolver.userPublicIds(userIds);

        ConversationMember mine = ms.stream()
                .filter(m -> m.getUserId().equals(currentUserId)).findFirst().orElse(null);
        Long lastRead = mine == null || mine.getLastReadMsgId() == null ? 0L : mine.getLastReadMsgId();

        // 消息 id → public_id（lastMessage + 我的已读游标）
        Set<Long> msgIds = new LinkedHashSet<>();
        if (c.getLastMessageId() != null) msgIds.add(c.getLastMessageId());
        if (lastRead > 0) msgIds.add(lastRead);
        Map<Long, String> msgPub = messagePublicIds(msgIds);

        vo.setMemberIds(memberNumIds.stream().map(userPub::get).toList());
        vo.setOwnerId(c.getOwnerId() == null ? null : userPub.get(c.getOwnerId()));
        vo.setLastMessageId(c.getLastMessageId() == null ? null : msgPub.get(c.getLastMessageId()));
        vo.setLastReadMsgId(lastRead > 0 ? msgPub.get(lastRead) : null);
        vo.setMyRole(mine == null ? null : mine.getRole());
        vo.setUnreadCount(countUnread(c.getId(), currentUserId, lastRead));
        return vo;
    }

    /** 批量：数字消息 id → public_id 映射（本库 messages 表）。 */
    private Map<Long, String> messagePublicIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
        List<com.lynn.nook.im.entity.Message> rows = messageMapper.selectListByQuery(QueryWrapper.create()
                .select("id", "public_id")
                .where("id in (" + placeholders + ")", ids.toArray()));
        Map<Long, String> map = new HashMap<>();
        for (com.lynn.nook.im.entity.Message m : rows) {
            map.put(m.getId(), m.getPublicId());
        }
        return map;
    }

    /** 获取会话成员 userId 列表（不含会话校验）。 */
    public List<Long> listMemberIds(Long conversationId) {
        List<ConversationMember> ms = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id = ?", conversationId));
        return ms.stream().map(ConversationMember::getUserId).toList();
    }

    /** 已读该消息的成员 userId（last_read_msg_id >= messageId），排除发送者本人。 */
    public List<Long> readersOf(Long conversationId, Long messageId, Long senderId) {
        List<ConversationMember> ms = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id = ?", conversationId)
                .and("user_id <> ?", senderId)
                .and("last_read_msg_id >= ?", messageId));
        return ms.stream().map(ConversationMember::getUserId).toList();
    }

    /** MessageService 写完消息后回调，更新 last_message_*。 */
    public void onMessageSent(Long conversationId, Long messageId, OffsetDateTime at) {
        Conversation c = conversationMapper.selectOneById(conversationId);
        if (c == null) return;
        c.setLastMessageId(messageId);
        c.setLastMessageAt(at);
        c.setUpdatedAt(at);
        conversationMapper.update(c);
    }
}
