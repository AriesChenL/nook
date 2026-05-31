package com.lynn.nook.im.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * nook-im 启动时幂等初始化 RustFS bucket：创建 + 公开读策略 + CORS（允许前端直传）。
 * 任一步失败仅告警、不阻断启动；策略/CORS 若 RustFS 不支持，可在控制台手动配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageInitializer implements ApplicationRunner {

    private final S3Client s3;
    private final StorageProperties props;

    @Override
    public void run(ApplicationArguments args) {
        String bucket = props.getBucket();
        try {
            ensureBucket(bucket);
        } catch (Exception e) {
            log.warn("RustFS bucket [{}] 创建失败，文件功能不可用，请检查存储服务：{}", bucket, e.getMessage());
            return;
        }
        applyQuietly("公开读策略", () -> applyPublicReadPolicy(bucket));
        applyQuietly("CORS", () -> applyCors(bucket));
        log.info("RustFS bucket [{}] 就绪", bucket);
    }

    private void ensureBucket(String bucket) {
        try {
            s3.headBucket(b -> b.bucket(bucket));
            return; // 已存在
        } catch (NoSuchBucketException ignored) {
            // 不存在 → 下面创建
        } catch (S3Exception e) {
            if (e.statusCode() != 404) throw e;
        }
        s3.createBucket(b -> b.bucket(bucket));
        log.info("已创建 RustFS bucket [{}]", bucket);
    }

    private void applyPublicReadPolicy(String bucket) {
        String policy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}"""
                .formatted(bucket);
        s3.putBucketPolicy(b -> b.bucket(bucket).policy(policy));
    }

    private void applyCors(String bucket) {
        CORSRule rule = CORSRule.builder()
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "PUT", "HEAD")
                .allowedHeaders("*")
                .exposeHeaders("ETag")
                .maxAgeSeconds(3600)
                .build();
        s3.putBucketCors(b -> b.bucket(bucket).corsConfiguration(c -> c.corsRules(rule)));
    }

    private void applyQuietly(String what, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("RustFS {} 设置失败（RustFS 可能不支持该 S3 接口），请在控制台手动配置：{}", what, e.getMessage());
        }
    }
}
