package com.lynn.nook.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFriendRequest {

    @NotNull
    private Long toUserId;

    @Size(max = 255)
    private String message;
}
