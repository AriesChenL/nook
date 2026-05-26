package com.lynn.nook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名为 3-32 位字母/数字/下划线")
    private String username;

    @NotBlank
    @Size(min = 6, max = 64, message = "密码长度 6-64")
    private String password;

    private String nickname;
}
