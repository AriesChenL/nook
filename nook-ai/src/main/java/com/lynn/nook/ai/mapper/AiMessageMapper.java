package com.lynn.nook.ai.mapper;

import com.lynn.nook.ai.entity.AiMessage;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /** 取某会话的全部消息，按主键升序（即时间顺序）。 */
    default List<AiMessage> listBySession(Long sessionId) {
        return selectListByQuery(QueryWrapper.create()
                .where("session_id = ?", sessionId)
                .orderBy("id asc"));
    }
}
