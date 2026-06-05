package com.lynn.nook.user.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 与 nook-auth 共享 users 表的实体（仅读 + 资料字段更新）。
 * 不包含 password_hash 字段，nook-user 永远不需要操作密码。
 */
@Data
@Table("users")
public class UserAccount {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 对外不可枚举的业务标识（UUID）。内部主键仍用数字 id。 */
    @Column("public_id")
    private String publicId;

    private String username;

    private String nickname;

    @Column("avatar_url")
    private String avatarUrl;

    private String email;

    private String phone;

    /** 1=正常 0=禁用 */
    private Short status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
