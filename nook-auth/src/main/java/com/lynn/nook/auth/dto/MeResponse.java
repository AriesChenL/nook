package com.lynn.nook.auth.dto;

/**
 * @param id 用户对外标识：public_id（脱敏，字符串）
 */
public record MeResponse(String id, String username, String nickname, String avatarUrl,
                         String email, String phone, Short status) {
}
