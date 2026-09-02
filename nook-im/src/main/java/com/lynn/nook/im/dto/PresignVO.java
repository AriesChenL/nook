package com.lynn.nook.im.dto;

/**
 * 预签名结果：前端用 uploadUrl 直传 RustFS，发消息时回传 downloadUrl 等元数据。
 *
 * @param uploadUrl     预签名 PUT 地址，前端直接 PUT 文件到此（短期有效）
 * @param downloadUrl   公开读下载/预览地址（永久有效）
 * @param objectKey     对象 key
 * @param mediaType     规范化后的 MIME
 * @param expireSeconds uploadUrl 有效期（秒）
 */
public record PresignVO(String uploadUrl, String downloadUrl, String objectKey,
                        String mediaType, long expireSeconds) {
}
