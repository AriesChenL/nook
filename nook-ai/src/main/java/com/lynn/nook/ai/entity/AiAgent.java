package com.lynn.nook.ai.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户私有 AI Agent。
 * <p>{@code id} 转字符串 {@code "agent-" + id} 作为 agentscope {@code HarnessAgent} 的稳定 agentId；
 * {@code persona} 注入 {@code sysPrompt}（人格各 Agent 独立，不落工作区文件）。
 */
@Data
@Table("ai_agent")
public class AiAgent {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 对外不可枚举标识（UUID 文本），用于 API/URL/UI；内部仍用数字主键 id。 */
    @Column("public_id")
    private String publicId;

    @Column("owner_user_id")
    private Long ownerUserId;

    private String name;

    /** 人格设定，构建 HarnessAgent 时注入 sysPrompt */
    private String persona;

    @Column("avatar_url")
    private String avatarUrl;

    @Column("model_name")
    private String modelName;

    /** 1 正常 / 0 停用 */
    private Short status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
