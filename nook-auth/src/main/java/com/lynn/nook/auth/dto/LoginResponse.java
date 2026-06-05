package com.lynn.nook.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    /** 用户对外标识：public_id（脱敏，字符串）。已移除数字 userId。 */
    private String userId;
    private String username;
    private String nickname;
    private String token;
    private long expireSeconds;
}
