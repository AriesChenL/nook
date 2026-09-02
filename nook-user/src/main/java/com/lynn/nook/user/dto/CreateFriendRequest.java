package com.lynn.nook.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param toUserId 目标用户的对外标识 public_id（脱敏，字符串），入口解析回数字主键
 * @param message  申请附言
 */
public record CreateFriendRequest(

        @NotNull
        String toUserId,

        @Size(max = 255)
        String message
) {
}
