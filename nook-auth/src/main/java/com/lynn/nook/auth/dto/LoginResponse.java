package com.lynn.nook.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String token;
    private long expireSeconds;
}
