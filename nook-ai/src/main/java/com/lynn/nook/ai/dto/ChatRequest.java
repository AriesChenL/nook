package com.lynn.nook.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param sessionId 对话会话 public_id；缺省时由 service 取/建该 Agent 的默认会话
 * @param content   用户输入正文
 */
public record ChatRequest(String sessionId, @NotBlank String content) {
}
