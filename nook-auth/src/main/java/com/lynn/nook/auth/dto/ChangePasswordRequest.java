package com.lynn.nook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank
        String oldPassword,

        @NotBlank
        @Size(min = 6, max = 64, message = "密码长度 6-64")
        String newPassword
) {
}
