package com.lynn.nook.user.dto;

import java.time.OffsetDateTime;

public record FriendVO(UserVO user, String remark, OffsetDateTime createdAt) {
}
