package com.lynn.nook.ai.mapper;

import com.lynn.nook.ai.entity.AiChatSession;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;

public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    /** public_id → 数字主键 id；解析不到返回 null（由调用方抛资源不存在）。 */
    default Long selectIdByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        AiChatSession s = selectOneByQuery(QueryWrapper.create()
                .select("id")
                .where("public_id = ?", publicId));
        return s == null ? null : s.getId();
    }
}
