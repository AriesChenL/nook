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

    private Long id;
    private Short type;
    private String name;
    private String avatarUrl;
    private Long ownerId;
    private Long lastMessageId;
    private OffsetDateTime lastMessageAt;
    private List<Long> memberIds;
    /** 当前用户在该会话中的角色：1=普通 2=管理员 3=群主（单聊恒为 1） */
    private Short myRole;
    /** 当前用户在该会话中已读到的最大消息 id */
    private Long lastReadMsgId;
    /** 未读数（消息 id 大于 lastReadMsgId 的条数） */
    private Long unreadCount;

    public static ConversationVO from(Conversation c) {
        if (c == null) return null;
        return ConversationVO.builder()
                .id(c.getId())
                .type(c.getType())
                .name(c.getName())
                .avatarUrl(c.getAvatarUrl())
                .ownerId(c.getOwnerId())
                .lastMessageId(c.getLastMessageId())
                .lastMessageAt(c.getLastMessageAt())
                .build();
    }
}
