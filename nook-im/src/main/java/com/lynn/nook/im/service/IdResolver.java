package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.client.UserClient;
import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.dto.UserBriefVO;
import com.lynn.nook.im.entity.Conversation;
import com.lynn.nook.im.entity.Message;
import com.lynn.nook.im.mapper.ConversationMapper;
import com.lynn.nook.im.mapper.MessageMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * public_id ↔ 数字 id 的边界转换中枢。
 * <ul>
 *   <li>会话/消息：im 自己拥有，直接查本库 public_id ↔ 数字主键。</li>
 *   <li>用户：im 只持有数字 user id，public_id 归 nook-user。
 *       public→数字经 {@link UserClient#resolve}；数字→public + 资料经 {@link UserClient#listByIds}
 *       取资料、再经 resolve 反查把 public_id 对回数字（/user/batch 返回的 UserVO 已不含数字 id）。</li>
 * </ul>
 * 引用 id 的解析不到一律抛资源不存在；资料聚合（昵称/头像）可降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdResolver {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserClient userClient;

    // ---------- conversation ----------

    /** 会话 public_id → 数字主键；不存在抛 CONVERSATION_NOT_FOUND。 */
    public Long conversationId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        Conversation c = conversationMapper.selectOneByQuery(QueryWrapper.create()
                .select("id")
                .where("public_id = ?", publicId));
        if (c == null || c.getId() == null) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        return c.getId();
    }

    /** 数字会话 id → public_id（单个）。取不到返回 null。 */
    public String conversationPublicId(Long id) {
        if (id == null) return null;
        Conversation c = conversationMapper.selectOneByQuery(QueryWrapper.create()
                .select("public_id")
                .where("id = ?", id));
        return c == null ? null : c.getPublicId();
    }

    /** 批量：数字会话 id → public_id 映射。 */
    public Map<Long, String> conversationPublicIds(Collection<Long> ids) {
        Set<Long> distinct = distinctLongs(ids);
        if (distinct.isEmpty()) return Map.of();
        String placeholders = distinct.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
        List<Conversation> rows = conversationMapper.selectListByQuery(QueryWrapper.create()
                .select("id", "public_id")
                .where("id in (" + placeholders + ")", distinct.toArray()));
        Map<Long, String> map = new HashMap<>();
        for (Conversation c : rows) {
            map.put(c.getId(), c.getPublicId());
        }
        return map;
    }

    // ---------- message ----------

    /** 消息 public_id → 数字主键；不存在抛 MESSAGE_NOT_FOUND。 */
    public Long messageId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BusinessException(ResultCode.MESSAGE_NOT_FOUND);
        }
        Message m = messageMapper.selectOneByQuery(QueryWrapper.create()
                .select("id")
                .where("public_id = ?", publicId));
        if (m == null || m.getId() == null) {
            throw new BusinessException(ResultCode.MESSAGE_NOT_FOUND);
        }
        return m.getId();
    }

    // ---------- message VO 装配 ----------

    /**
     * 单条消息 → 脱敏 VO：填好消息 public_id（来自实体）、conversationId（会话 public_id）、
     * senderId（发送者 user public_id）。会话 public_id 与发送者 public_id 各查一次。
     * <p>用于发消息/撤回/系统消息等单条场景。
     *
     * @param convPublicId 已知的会话 public_id；为 null 时按 m.conversationId 反查。
     */
    public MessageVO toMessageVO(Message m, String convPublicId) {
        if (m == null) return null;
        MessageVO vo = MessageVO.from(m);
        String cpid = convPublicId != null ? convPublicId : conversationPublicId(m.getConversationId());
        vo.setConversationId(cpid);
        Map<Long, String> userPub = userPublicIds(List.of(m.getSenderId()));
        vo.setSenderId(userPub.get(m.getSenderId()));
        return vo;
    }

    /**
     * 批量消息 → 脱敏 VO 列表（同会话）。一次性把全部 senderId 换成 public_id，避免逐条调用。
     *
     * @param convPublicId 这些消息所属会话的 public_id（同一会话）。
     */
    public List<MessageVO> toMessageVOs(List<Message> messages, String convPublicId) {
        if (messages == null || messages.isEmpty()) return List.of();
        Set<Long> senderIds = new LinkedHashSet<>();
        for (Message m : messages) {
            if (m.getSenderId() != null) senderIds.add(m.getSenderId());
        }
        Map<Long, String> userPub = userPublicIds(senderIds);
        return messages.stream().map(m -> {
            MessageVO vo = MessageVO.from(m);
            vo.setConversationId(convPublicId);
            vo.setSenderId(userPub.get(m.getSenderId()));
            return vo;
        }).toList();
    }

    // ---------- user (跨 nook-user) ----------

    /**
     * 单个 user public_id → 数字 userId；解析不到抛 USER_NOT_FOUND。
     * 建直聊/找人这类入口必须拿到准确数字 id，不能降级。
     */
    public Long userId(String publicId) {
        Map<String, Long> map = resolveUserIds(List.of(publicId == null ? "" : publicId));
        Long id = map.get(publicId);
        if (id == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return id;
    }

    /** 批量 user public_id → 数字 userId 映射。解析不到的 public_id 不出现在结果里。 */
    public Map<String, Long> resolveUserIds(Collection<String> publicIds) {
        Set<String> distinct = distinctStrings(publicIds);
        if (distinct.isEmpty()) return Map.of();
        Result<Map<String, Long>> resp = userClient.resolve(List.copyOf(distinct));
        if (resp == null || resp.getData() == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return resp.getData();
    }

    /**
     * 批量取用户资料并对回数字 userId：{@code 数字 userId -> UserBriefVO(id=public_id, 昵称/头像)}。
     * 先经 /user/batch 取资料（其 id 已是 public_id），再经 /user/resolve 把 public_id 反查回数字，
     * 因为 /user/batch 返回的视图已不含数字 id，无法直接按输入顺序对齐。
     * <p>nook-user 不可用时返回空 map，由上层按语义决定是否降级。
     */
    public Map<Long, UserBriefVO> userProfilesByNumericId(Collection<Long> numericIds) {
        Set<Long> distinct = distinctLongs(numericIds);
        if (distinct.isEmpty()) return Map.of();
        try {
            Result<List<UserBriefVO>> resp = userClient.listByIds(List.copyOf(distinct));
            if (resp == null || resp.getData() == null) return Map.of();
            List<UserBriefVO> briefs = resp.getData().stream()
                    .filter(u -> u.id() != null)
                    .toList();
            if (briefs.isEmpty()) return Map.of();

            // public_id -> 数字 userId，反查得到数字键
            Map<String, Long> pubToNum = resolveUserIds(briefs.stream().map(UserBriefVO::id).toList());
            Map<Long, UserBriefVO> byNum = new HashMap<>();
            for (UserBriefVO b : briefs) {
                Long num = pubToNum.get(b.id());
                if (num != null) byNum.put(num, b);
            }
            return byNum;
        } catch (Exception e) {
            log.warn("聚合用户资料失败，降级为空：{}", e.getMessage());
            return Map.of();
        }
    }

    /** 仅要数字 userId → public_id 映射（不含昵称/头像）。基于 {@link #userProfilesByNumericId}。 */
    public Map<Long, String> userPublicIds(Collection<Long> numericIds) {
        Map<Long, UserBriefVO> profiles = userProfilesByNumericId(numericIds);
        Map<Long, String> map = new HashMap<>();
        profiles.forEach((num, brief) -> map.put(num, brief.id()));
        return map;
    }

    // ---------- helpers ----------

    private static Set<Long> distinctLongs(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) set.add(id);
        }
        return set;
    }

    private static Set<String> distinctStrings(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        Set<String> set = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) set.add(id);
        }
        return set;
    }
}
