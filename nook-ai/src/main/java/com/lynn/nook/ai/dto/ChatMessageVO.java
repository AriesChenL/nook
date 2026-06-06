package com.lynn.nook.ai.dto;

import com.lynn.nook.ai.entity.AiMessage;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

/** AI 对话历史消息（对外展示）。 */
@Data
@AllArgsConstructor
public class ChatMessageVO {

    /** 消息 public_id（对外不可枚举标识） */
    private String id;
    /** user | assistant */
    private String role;
    private String content;
    private OffsetDateTime createdAt;

    public static ChatMessageVO from(AiMessage m) {
        return new ChatMessageVO(m.getPublicId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}
