package com.lynn.nook.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFriendRequest {

    /** 目标用户的对外标识 public_id（脱敏，字符串），入口解析回数字主键。 */
    @NotNull
    private String toUserId;

    @Size(max = 255)
    private String message;
}
