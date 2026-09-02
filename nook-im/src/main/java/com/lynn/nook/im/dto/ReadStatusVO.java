package com.lynn.nook.im.dto;

import java.util.List;

/**
 * 消息已读状态：群聊「已读 readCount/totalRecipients」。发送者不计入接收方。
 *
 * @param messageId       消息 public_id
 * @param conversationId  会话 public_id
 * @param totalRecipients 应收方人数（会话成员数减去发送者本人）
 * @param readCount       已读人数
 * @param readerUserIds   已读成员的 user public_id 列表
 */
public record ReadStatusVO(String messageId, String conversationId, int totalRecipients,
                           int readCount, List<String> readerUserIds) {
}
