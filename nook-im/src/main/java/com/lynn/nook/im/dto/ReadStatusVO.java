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

    /** 消息 public_id。 */
    private String messageId;
    /** 会话 public_id。 */
    private String conversationId;
    /** 应收方人数（会话成员数减去发送者本人） */
    private int totalRecipients;
    /** 已读人数 */
    private int readCount;
    /** 已读成员的 user public_id 列表。 */
    private List<String> readerUserIds;
}
