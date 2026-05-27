package com.lynn.nook.im.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("conversation_members")
public class ConversationMember {

    public static final short ROLE_MEMBER = 1;
    public static final short ROLE_ADMIN  = 2;
    public static final short ROLE_OWNER  = 3;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("conversation_id")
    private Long conversationId;

    @Column("user_id")
    private Long userId;

    private Short role;

    @Column("last_read_msg_id")
    private Long lastReadMsgId;

    private Short mute;

    @Column("joined_at")
    private OffsetDateTime joinedAt;
}
