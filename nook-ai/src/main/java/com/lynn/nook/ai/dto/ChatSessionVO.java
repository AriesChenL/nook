package com.lynn.nook.ai.dto;

import com.lynn.nook.ai.entity.AiChatSession;

import java.time.OffsetDateTime;

/**
 * @param id        session public_id（对外不可枚举标识，非数字主键）
 * @param agentId   所属 agent 的 public_id
 * @param title     会话标题
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ChatSessionVO(String id, String agentId, String title,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    /**
     * @param s             会话实体（提供 session public_id）
     * @param agentPublicId 所属 agent 的 public_id（session 实体只持有数字 agent_id，需外部解析后传入）
     */
    public static ChatSessionVO from(AiChatSession s, String agentPublicId) {
        return new ChatSessionVO(s.getPublicId(), agentPublicId, s.getTitle(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
