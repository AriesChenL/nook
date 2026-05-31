package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 申请文件直传的预签名 URL。
 */
@Data
public class PresignRequest {

    /** 原始文件名（用于推断扩展名、回显）。 */
    @NotBlank
    private String fileName;

    /** 文件 MIME 类型，如 image/png、video/mp4。 */
    @NotBlank
    private String mimeType;

    /** 文件大小（字节），用于上限校验。 */
    @Positive
    private long size;
}
