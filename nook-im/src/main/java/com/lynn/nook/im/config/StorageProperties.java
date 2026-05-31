package com.lynn.nook.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置（RustFS / S3 兼容）。绑定 application.yml 的 nook.storage.*。
 */
@Data
@Component
@ConfigurationProperties(prefix = "nook.storage")
public class StorageProperties {

    /** S3 endpoint，须为浏览器可达地址（presigned URL 的 host）。 */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket;

    /** 下载/预览前缀（bucket 公开读），形如 http://host:9000/{bucket}。 */
    private String publicBaseUrl;

    /** 预签名上传 URL 有效期（秒）。 */
    private long presignExpireSeconds = 600;

    /** 单文件上限（字节）。 */
    private long maxFileSize = 209715200L;
}
