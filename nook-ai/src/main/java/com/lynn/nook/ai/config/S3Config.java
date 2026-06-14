package com.lynn.nook.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * agent 工作区对象存储客户端：单个 {@link S3Client}，供 {@code S3Filesystem} 读写裸对象。
 * RustFS 不校验 region（任意值），且需 path-style 访问。
 */
@Configuration
public class S3Config {

    private static final Region REGION = Region.US_EAST_1;

    /** destroyMethod=close：容器关闭时释放底层 HTTP 连接池。 */
    @Bean(name = "aiS3Client", destroyMethod = "close")
    public S3Client aiS3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .region(REGION)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
