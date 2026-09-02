package com.lynn.nook.ai.dto;

/**
 * @param sessionId 对话所属 session 的 public_id（对外不可枚举标识）
 * @param reply     助手回复正文
 */
public record ChatReplyVO(String sessionId, String reply) {
}
