package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId 目标会话的 public_id
 * @param contentType    1=text 2=image 3=file；缺省为 text
 * @param content        文本消息正文；文件消息可空（由后端用 fileName 兜底）
 * @param fileUrl        文件消息（contentType=2/3）：下载/预览地址（预签名上传后由前端回传）
 */
public record SendMessageRequest(

        @NotBlank
        String conversationId,

        Short contentType,

        @Size(max = 8000)
        String content,

        String fileUrl,

        @Size(max = 255)
        String fileName,

        Long fileSize,

        @Size(max = 128)
        String mediaType
) {
}
