package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.config.StorageProperties;
import com.lynn.nook.im.dto.PresignRequest;
import com.lynn.nook.im.dto.PresignVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 文件直传：为客户端签发 RustFS 的预签名 PUT URL；下载走 bucket 公开读地址。
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    /** image/ video/ audio/ text/ 走前缀放行，其余 application/* 用白名单。 */
    private static final Set<String> ALLOWED_APP_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/json",
            "application/octet-stream"
    );

    private final StorageProperties props;
    private final S3Presigner presigner;

    public PresignVO presignPut(Long userId, PresignRequest req) {
        long size = req.size();
        if (size <= 0 || size > props.getMaxFileSize()) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }
        String mime = normalizeMime(req.mimeType());
        if (!allowed(mime)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }

        String key = buildKey(userId, req.fileName());
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(mime)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignExpireSeconds()))
                .putObjectRequest(objectRequest)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        return new PresignVO(
                presigned.url().toString(),
                props.getPublicBaseUrl() + "/" + key,
                key,
                mime,
                props.getPresignExpireSeconds());
    }

    private boolean allowed(String mime) {
        return mime.startsWith("image/") || mime.startsWith("video/")
                || mime.startsWith("audio/") || mime.startsWith("text/")
                || ALLOWED_APP_TYPES.contains(mime);
    }

    private String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) return "application/octet-stream";
        int i = mime.indexOf(';');
        return (i > 0 ? mime.substring(0, i) : mime).trim().toLowerCase();
    }

    private String buildKey(Long userId, String fileName) {
        String ext = ext(fileName);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "im/" + userId + "/" + uuid + (ext.isEmpty() ? "" : "." + ext);
    }

    private String ext(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        if (i < 0 || i == fileName.length() - 1) return "";
        String e = fileName.substring(i + 1).toLowerCase();
        return e.matches("[a-z0-9]{1,8}") ? e : "";
    }
}
