package com.lynn.nook.im.dto;

import com.lynn.nook.im.entity.Conversation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {

    /** 会话 public_id（脱敏对外标识）。 */
    private String id;
    private Short type;
    private String name;
    private String avatarUrl;
    /** 群主的 user public_id。 */
    private String ownerId;
    /** 最后一条消息的 public_id。 */
    private String lastMessageId;
    private OffsetDateTime lastMessageAt;
    /**
     * 最后一条消息的完整内容（发送者已脱敏为 public_id，撤回消息按 {@link MessageVO} 规则屏蔽原文）。
     * 会话列表直接带出，前端无需再逐会话拉最后一条（去 N+1）。无消息时为 null。
     */
    private MessageVO lastMessage;
    /** 成员的 user public_id 列表。 */
    private List<String> memberIds;
    /** 当前用户在该会话中的角色：1=普通 2=管理员 3=群主（单聊恒为 1） */
    private Short myRole;
    /** 当前用户在该会话中已读到的最大消息 public_id。 */
    private String lastReadMsgId;
    /** 未读数（消息 id 大于 lastReadMsgId 的条数） */
    private Long unreadCount;

    /**
     * 仅拷贝会话自身的非 id 字段（type/name/avatar/lastMessageAt）与会话自己的 public_id。
     * ownerId/memberIds/lastMessageId/lastReadMsgId 等跨实体引用由 service 层脱敏后回填。
     */
    public static ConversationVO from(Conversation c) {
        if (c == null) return null;
        return ConversationVO.builder()
                .id(c.getPublicId())
                .type(c.getType())
                .name(c.getName())
                .avatarUrl(c.getAvatarUrl())
                .lastMessageAt(c.getLastMessageAt())
                .build();
    }
}
