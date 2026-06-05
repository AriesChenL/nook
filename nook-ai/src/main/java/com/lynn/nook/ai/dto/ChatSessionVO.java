package com.lynn.nook.ai.dto;

import com.lynn.nook.ai.entity.AiChatSession;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ChatSessionVO {

    /** session public_id（对外不可枚举标识，非数字主键） */
    private String id;
    /** 所属 agent 的 public_id */
    private String agentId;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * @param s              会话实体（提供 session public_id）
     * @param agentPublicId  所属 agent 的 public_id（session 实体只持有数字 agent_id，需外部解析后传入）
     */
    public static ChatSessionVO from(AiChatSession s, String agentPublicId) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.id = s.getPublicId();
        vo.agentId = agentPublicId;
        vo.title = s.getTitle();
        vo.createdAt = s.getCreatedAt();
        vo.updatedAt = s.getUpdatedAt();
        return vo;
    }
}
