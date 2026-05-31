package com.lynn.nook.im.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 预签名结果：前端用 uploadUrl 直传 RustFS，发消息时回传 downloadUrl 等元数据。
 */
@Data
@Builder
public class PresignVO {

    /** 预签名 PUT 地址，前端直接 PUT 文件到此（短期有效）。 */
    private String uploadUrl;

    /** 公开读下载/预览地址（永久有效）。 */
    private String downloadUrl;

    /** 对象 key。 */
    private String objectKey;

    /** 规范化后的 MIME。 */
    private String mediaType;

    /** uploadUrl 有效期（秒）。 */
    private long expireSeconds;
}
