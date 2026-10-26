package com.sinai.storage.internal;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.storage.api.ObjectStorage;
import com.sinai.storage.config.PresignMinioClient;
import io.minio.DownloadObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.http.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MinioObjectStorage implements ObjectStorage {

    private static final Duration MAX_PRESIGN_EXPIRY = Duration.ofDays(7);
    private static final String RESPONSE_CONTENT_DISPOSITION = "response-content-disposition";

    private final MinioClient minioClient;
    private final MinioClient presignClient;

    public MinioObjectStorage(MinioClient minioClient) {
        this.minioClient = minioClient;
        this.presignClient = minioClient;
    }

    @Autowired
    public MinioObjectStorage(MinioClient minioClient, PresignMinioClient presignMinioClient) {
        this.minioClient = minioClient;
        this.presignClient = presignMinioClient.client();
    }

    @Override
    public void upload(String bucket, String key, Path file, String contentType) {
        requireLocation(bucket, key);
        if (Objects.isNull(file)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "file must not be null");
        }
        if (!Files.isRegularFile(file)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "File does not exist: " + file);
        }
        try {
            var builder = UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .filename(file.toString());
            if (StringUtils.isNotBlank(contentType)) {
                builder.contentType(contentType);
            }
            minioClient.uploadObject(builder.build());
        } catch (Exception e) {
            throw new SinAiException(ErrorCode.STORAGE_ERROR, "Failed to upload " + bucket + "/" + key, e);
        }
    }

    @Override
    public void download(String bucket, String key, Path target) {
        requireLocation(bucket, key);
        if (Objects.isNull(target)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "target must not be null");
        }
        try {
            Files.deleteIfExists(target);
            minioClient.downloadObject(DownloadObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .filename(target.toString())
                    .build());
        } catch (Exception e) {
            throw new SinAiException(ErrorCode.STORAGE_ERROR, "Failed to download " + bucket + "/" + key, e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        requireLocation(bucket, key);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new SinAiException(ErrorCode.STORAGE_ERROR, "Failed to delete " + bucket + "/" + key, e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String key, Duration expiry) {
        return presignedGetUrl(bucket, key, expiry, null);
    }

    @Override
    public String presignedGetUrl(String bucket, String key, Duration expiry, String contentDisposition) {
        requireLocation(bucket, key);
        if (Objects.isNull(expiry)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "expiry must not be null");
        }
        if (expiry.isZero() || expiry.isNegative()) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "expiry must be positive");
        }
        if (expiry.compareTo(MAX_PRESIGN_EXPIRY) > 0) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "expiry must not exceed " + MAX_PRESIGN_EXPIRY);
        }
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) expiry.toSeconds())
                    .extraQueryParams(StringUtils.isBlank(contentDisposition)
                            ? Map.of()
                            : Map.of(RESPONSE_CONTENT_DISPOSITION, contentDisposition))
                    .build());
        } catch (Exception e) {
            throw new SinAiException(ErrorCode.STORAGE_ERROR, "Failed to presign " + bucket + "/" + key, e);
        }
    }

    private void requireLocation(String bucket, String key) {
        if (StringUtils.isBlank(bucket)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "bucket must not be blank");
        }
        if (StringUtils.isBlank(key)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "key must not be blank");
        }
    }
}
