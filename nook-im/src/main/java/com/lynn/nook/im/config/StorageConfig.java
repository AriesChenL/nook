package com.lynn.nook.im.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 对象存储客户端：
 * - {@link S3Presigner} 签发预签名直传 URL（纯本地签名，不发 HTTP 请求）；
 * - {@link S3Client} 供启动初始化（建 bucket、公开读策略、CORS）使用。
 * RustFS 不校验 region（任意值），且需 path-style 访问。
 */
@Configuration
public class StorageConfig {

    private static final Region REGION = Region.US_EAST_1;

    private StaticCredentialsProvider credentials(StorageProperties props) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
    }

    private S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .credentialsProvider(credentials(props))
                .region(REGION)
                .serviceConfiguration(pathStyle())
                .build();
    }

    @Bean
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .credentialsProvider(credentials(props))
                .region(REGION)
                .serviceConfiguration(pathStyle())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
