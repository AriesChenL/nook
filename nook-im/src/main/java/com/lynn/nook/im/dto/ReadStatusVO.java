package com.lynn.nook.im.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 消息已读状态：群聊「已读 readCount/totalRecipients」。发送者不计入接收方。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadStatusVO {

    private Long messageId;
    private Long conversationId;
    /** 应收方人数（会话成员数减去发送者本人） */
    private int totalRecipients;
    /** 已读人数 */
    private int readCount;
    /** 已读成员 userId 列表 */
    private List<Long> readerUserIds;
}
