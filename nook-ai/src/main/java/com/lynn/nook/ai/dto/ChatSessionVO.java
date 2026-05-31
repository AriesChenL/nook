package com.lynn.nook.ai.dto;

import com.lynn.nook.ai.entity.AiChatSession;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ChatSessionVO {

    private Long id;
    private Long agentId;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ChatSessionVO from(AiChatSession s) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.id = s.getId();
        vo.agentId = s.getAgentId();
        vo.title = s.getTitle();
        vo.createdAt = s.getCreatedAt();
        vo.updatedAt = s.getUpdatedAt();
        return vo;
    }
}
