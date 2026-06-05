package com.lynn.nook.ai.mapper;

import com.lynn.nook.ai.entity.AiAgent;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;

public interface AiAgentMapper extends BaseMapper<AiAgent> {

    /** public_id → 数字主键 id；解析不到返回 null（由调用方抛资源不存在）。 */
    default Long selectIdByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        AiAgent a = selectOneByQuery(QueryWrapper.create()
                .select("id")
                .where("public_id = ?", publicId));
        return a == null ? null : a.getId();
    }
}
