package com.lynn.nook.user.dto;

import com.lynn.nook.user.entity.FriendRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestVO {

    /** 好友申请对外标识：public_id（脱敏，字符串）。 */
    private String id;
    private UserVO fromUser;
    private UserVO toUser;
    private String message;
    private Short status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static FriendRequestVO from(FriendRequest r, UserVO fromUser, UserVO toUser) {
        return FriendRequestVO.builder()
                .id(r.getPublicId())
                .fromUser(fromUser)
                .toUser(toUser)
                .message(r.getMessage())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
