package com.lynn.nook.ai.dto;

import com.lynn.nook.ai.entity.AiAgent;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AgentVO {

    /** agent public_id（对外不可枚举标识，非数字主键） */
    private String id;
    private Long ownerUserId;
    private String name;
    private String persona;
    private String avatarUrl;
    private String modelName;
    private Short status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static AgentVO from(AiAgent a) {
        AgentVO vo = new AgentVO();
        vo.id = a.getPublicId();
        vo.ownerUserId = a.getOwnerUserId();
        vo.name = a.getName();
        vo.persona = a.getPersona();
        vo.avatarUrl = a.getAvatarUrl();
        vo.modelName = a.getModelName();
        vo.status = a.getStatus();
        vo.createdAt = a.getCreatedAt();
        vo.updatedAt = a.getUpdatedAt();
        return vo;
    }
}
