package com.lynn.nook.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatReplyVO {

    private Long sessionId;
    private String reply;
}
