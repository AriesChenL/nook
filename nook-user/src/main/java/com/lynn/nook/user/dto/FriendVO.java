package com.lynn.nook.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendVO {
    private UserVO user;
    private String remark;
    private OffsetDateTime createdAt;
}
