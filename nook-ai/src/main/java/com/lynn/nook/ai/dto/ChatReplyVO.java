package com.lynn.nook.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatReplyVO {

    /** 对话所属 session 的 public_id（对外不可枚举标识） */
    private String sessionId;
    private String reply;
}
