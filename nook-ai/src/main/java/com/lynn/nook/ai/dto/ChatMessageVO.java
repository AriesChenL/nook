package com.lynn.nook.ai.dto;

import com.lynn.nook.ai.entity.AiMessage;

import java.time.OffsetDateTime;

/**
 * AI 对话历史消息（对外展示）。
 *
 * @param id        消息 public_id（对外不可枚举标识）
 * @param role      user | assistant
 * @param content   消息正文
 * @param trace     assistant 推理过程的 steps JSON（思考分段 + 工具调用）；user 为 null。前端解析后还原过程展示
 * @param createdAt 创建时间
 */
public record ChatMessageVO(String id, String role, String content, String trace, OffsetDateTime createdAt) {

    public static ChatMessageVO from(AiMessage m) {
        return new ChatMessageVO(m.getPublicId(), m.getRole(), m.getContent(), m.getTrace(), m.getCreatedAt());
    }
}
