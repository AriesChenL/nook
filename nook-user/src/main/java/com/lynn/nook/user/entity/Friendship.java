package com.lynn.nook.user.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("friendships")
public class Friendship {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("owner_id")
    private Long ownerId;

    @Column("friend_id")
    private Long friendId;

    private String remark;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
