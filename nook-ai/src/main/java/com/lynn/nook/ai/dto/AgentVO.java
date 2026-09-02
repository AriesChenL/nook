package com.lynn.nook.ai.dto;

import com.lynn.nook.ai.entity.AiAgent;

import java.time.OffsetDateTime;

/**
 * @param id agent public_id（对外不可枚举标识，非数字主键）
 */
public record AgentVO(String id, Long ownerUserId, String name, String persona, String avatarUrl,
                      String modelName, Short status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static AgentVO from(AiAgent a) {
        return new AgentVO(a.getPublicId(), a.getOwnerUserId(), a.getName(), a.getPersona(),
                a.getAvatarUrl(), a.getModelName(), a.getStatus(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
