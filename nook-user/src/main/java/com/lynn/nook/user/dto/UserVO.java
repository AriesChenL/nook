package com.lynn.nook.user.dto;

import com.lynn.nook.user.entity.UserAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 对外返回的用户视图，绝不携带敏感字段。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private String phone;

    public static UserVO from(UserAccount u) {
        if (u == null) return null;
        return UserVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nickname(u.getNickname())
                .avatarUrl(u.getAvatarUrl())
                .email(u.getEmail())
                .phone(u.getPhone())
                .build();
    }

    /** 对非自己人的精简视图：去除 email/phone。 */
    public static UserVO fromPublic(UserAccount u) {
        if (u == null) return null;
        return UserVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nickname(u.getNickname())
                .avatarUrl(u.getAvatarUrl())
                .build();
    }
}
