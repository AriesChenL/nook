package com.lynn.nook.user.dto;

import com.lynn.nook.user.entity.UserAccount;

/**
 * 对外返回的用户视图，绝不携带敏感字段。
 *
 * @param id 用户对外标识：public_id（脱敏，字符串）
 */
public record UserVO(String id, String username, String nickname, String avatarUrl, String email, String phone) {

    public static UserVO from(UserAccount u) {
        if (u == null) return null;
        return new UserVO(u.getPublicId(), u.getUsername(), u.getNickname(),
                u.getAvatarUrl(), u.getEmail(), u.getPhone());
    }

    /** 对非自己人的精简视图：去除 email/phone。 */
    public static UserVO fromPublic(UserAccount u) {
        if (u == null) return null;
        return new UserVO(u.getPublicId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(), null, null);
    }
}
