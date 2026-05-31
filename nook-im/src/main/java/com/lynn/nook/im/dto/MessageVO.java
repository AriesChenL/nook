package com.lynn.nook.im.dto;

import com.lynn.nook.im.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private Short contentType;
    private String content;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mediaType;
    private Short recalled;
    private OffsetDateTime recalledAt;
    private OffsetDateTime createdAt;

    public static MessageVO from(Message m) {
        if (m == null) return null;
        boolean isRecalled = m.getRecalled() != null && m.getRecalled() == 1;
        return MessageVO.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .contentType(m.getContentType())
                // 撤回后历史回放不返回原文/文件信息，避免泄露
                .content(isRecalled ? null : m.getContent())
                .fileUrl(isRecalled ? null : m.getFileUrl())
                .fileName(isRecalled ? null : m.getFileName())
                .fileSize(isRecalled ? null : m.getFileSize())
                .mediaType(isRecalled ? null : m.getMediaType())
                .recalled(m.getRecalled() == null ? 0 : m.getRecalled())
                .recalledAt(m.getRecalledAt())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
