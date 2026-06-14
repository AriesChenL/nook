package com.lynn.nook.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * agent 工作区对象存储配置（RustFS / S3 兼容）。绑定 application.yml 的 {@code nook.ai.storage.*}。
 *
 * <p>独立于 nook-im 的 {@code nook.storage}：本模块只存 agent 产出的工作区文件（裸对象），
 * 不签发 presigned URL，故无 public-base-url / 过期时间等字段。
 */
@Data
@Component
@ConfigurationProperties(prefix = "nook.ai.storage")
public class StorageProperties {

    /** S3 endpoint。 */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    /** agent 工作区文件所在 bucket。 */
    private String bucket = "nook-ai";
}
