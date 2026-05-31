package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull
    private Long conversationId;

    /** 1=text 2=image 3=file；缺省为 text */
    private Short contentType;

    /** 文本消息正文；文件消息可空（由后端用 fileName 兜底）。 */
    @Size(max = 8000)
    private String content;

    /** 文件消息（contentType=2/3）：下载/预览地址（预签名上传后由前端回传）。 */
    private String fileUrl;

    @Size(max = 255)
    private String fileName;

    private Long fileSize;

    @Size(max = 128)
    private String mediaType;
}
