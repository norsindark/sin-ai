package com.sinai.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.storage")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String rawBucket,
        String processedBucket,
        String region) {
}
