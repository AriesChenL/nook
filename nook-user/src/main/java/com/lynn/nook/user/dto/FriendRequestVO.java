package com.lynn.nook.user.dto;

import com.lynn.nook.user.entity.FriendRequest;

import java.time.OffsetDateTime;

/**
 * @param id 好友申请对外标识：public_id（脱敏，字符串）
 */
public record FriendRequestVO(String id, UserVO fromUser, UserVO toUser, String message,
                              Short status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static FriendRequestVO from(FriendRequest r, UserVO fromUser, UserVO toUser) {
        return new FriendRequestVO(r.getPublicId(), fromUser, toUser, r.getMessage(),
                r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
