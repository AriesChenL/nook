package com.lynn.nook.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(max = 64)
        String nickname,

        @Size(max = 512)
        String avatarUrl,

        @Email
        @Size(max = 128)
        String email,

        @Size(max = 32)
        String phone
) {
}
