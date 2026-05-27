package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.dto.ConversationVO;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper memberMapper;
    private final MessageMapper messageMapper;

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

        List<ConversationVO> result = new ArrayList<>(convs.size());
        for (Conversation c : convs) {
            ConversationVO vo = ConversationVO.from(c);
            vo.setMemberIds(membersByConv.getOrDefault(c.getId(), List.of()));
            ConversationMember mm = myMemberByConv.get(c.getId());
            Long lastRead = mm == null || mm.getLastReadMsgId() == null ? 0L : mm.getLastReadMsgId();
            vo.setLastReadMsgId(lastRead);
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
                .from("conversations c")
                .where("c.type = ?", Conversation.TYPE_DIRECT)
                .and("exists (select 1 from conversation_members m1 where m1.conversation_id = c.id and m1.user_id = ?)", a)
                .and("exists (select 1 from conversation_members m2 where m2.conversation_id = c.id and m2.user_id = ?)", b)
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
        List<ConversationMember> rows = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id in", convIds));
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
        vo.setMemberIds(ms.stream().map(ConversationMember::getUserId).toList());
        ConversationMember mine = ms.stream()
                .filter(m -> m.getUserId().equals(currentUserId)).findFirst().orElse(null);
        Long lastRead = mine == null || mine.getLastReadMsgId() == null ? 0L : mine.getLastReadMsgId();
        vo.setLastReadMsgId(lastRead);
        vo.setUnreadCount(countUnread(c.getId(), currentUserId, lastRead));
        return vo;
    }

    /** 获取会话成员 userId 列表（不含会话校验）。 */
    public List<Long> listMemberIds(Long conversationId) {
        List<ConversationMember> ms = memberMapper.selectListByQuery(QueryWrapper.create()
                .where("conversation_id = ?", conversationId));
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
