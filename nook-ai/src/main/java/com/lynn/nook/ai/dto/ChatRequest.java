package com.lynn.nook.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    /** 对话会话 id；缺省时由 service 取/建该 Agent 的默认会话 */
    private Long sessionId;

    @NotBlank
    private String content;
}
