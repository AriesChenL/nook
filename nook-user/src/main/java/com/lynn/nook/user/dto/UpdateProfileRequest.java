package com.lynn.nook.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 64)
    private String nickname;

    @Size(max = 512)
    private String avatarUrl;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;
}
