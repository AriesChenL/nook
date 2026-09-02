package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 申请文件直传的预签名 URL。
 *
 * @param fileName 原始文件名（用于推断扩展名、回显）
 * @param mimeType 文件 MIME 类型，如 image/png、video/mp4
 * @param size     文件大小（字节），用于上限校验
 */
public record PresignRequest(

        @NotBlank
        String fileName,

        @NotBlank
        String mimeType,

        @Positive
        long size
) {
}
