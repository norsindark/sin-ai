package com.sinai.storage.config;

import io.minio.MinioClient;

public record PresignMinioClient(MinioClient client) {
}
