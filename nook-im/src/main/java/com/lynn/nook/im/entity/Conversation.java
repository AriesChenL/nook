package com.lynn.nook.im.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("conversations")
public class Conversation {

    public static final short TYPE_DIRECT = 1;
    public static final short TYPE_GROUP  = 2;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Short type;

    private String name;

    @Column("avatar_url")
    private String avatarUrl;

    @Column("owner_id")
    private Long ownerId;

    @Column("last_message_id")
    private Long lastMessageId;

    @Column("last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
