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
    /** assistant 推理过程的 steps JSON（思考分段 + 工具调用）；user 为 null。前端解析后还原过程展示。 */
    private String trace;
    private OffsetDateTime createdAt;

    public static ChatMessageVO from(AiMessage m) {
        return new ChatMessageVO(m.getPublicId(), m.getRole(), m.getContent(), m.getTrace(), m.getCreatedAt());
    }
}
