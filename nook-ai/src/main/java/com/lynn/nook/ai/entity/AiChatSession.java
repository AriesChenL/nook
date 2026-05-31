package com.lynn.nook.ai.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * AI 对话会话（一个对话线程）。
 * <p>{@code id} 转字符串 {@code "sess-" + id} 作为 agentscope {@code RuntimeContext.sessionId}，
 * 用于隔离各对话线程的上下文（会话状态各 Agent / 各线程独立）。
 */
@Data
@Table("ai_chat_session")
public class AiChatSession {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("agent_id")
    private Long agentId;

    @Column("owner_user_id")
    private Long ownerUserId;

    private String title;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
