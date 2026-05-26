package com.lynn.nook.auth.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("users")
public class User {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String username;

    @Column("password_hash")
    private String passwordHash;

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
