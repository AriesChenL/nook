package com.lynn.nook.auth.dto;

/**
 * @param userId        用户对外标识：public_id（脱敏，字符串）。已移除数字 userId
 * @param username      用户名
 * @param nickname      昵称
 * @param token         JWT
 * @param expireSeconds token 有效期（秒）
 */
public record LoginResponse(String userId, String username, String nickname, String token, long expireSeconds) {
}
