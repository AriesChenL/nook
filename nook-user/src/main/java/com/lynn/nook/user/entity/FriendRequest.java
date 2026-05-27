package com.lynn.nook.user.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("friend_requests")
public class FriendRequest {

    /** 0=待处理 1=已接受 2=已拒绝 3=已撤回 */
    public static final short STATUS_PENDING  = 0;
    public static final short STATUS_ACCEPTED = 1;
    public static final short STATUS_REJECTED = 2;
    public static final short STATUS_REVOKED  = 3;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("from_user_id")
    private Long fromUserId;

    @Column("to_user_id")
    private Long toUserId;

    private String message;

    private Short status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
