package com.lynn.nook.ai.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * AI 对话可见历史消息（用于前端展示对话流）。
 * <p>与 agentscope 内部记忆解耦：本表只是"用户看到的对话记录"，每次 chat 落 user + assistant 两条。
 */
@Data
@Table("ai_message")
public class AiMessage {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 对外不可枚举标识（UUID 文本）。 */
    @Column("public_id")
    private String publicId;

    @Column("session_id")
    private Long sessionId;

    /** user | assistant */
    private String role;

    private String content;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
