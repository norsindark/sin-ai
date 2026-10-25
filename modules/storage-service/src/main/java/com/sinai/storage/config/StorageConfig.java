package com.sinai.storage.config;

import io.minio.MinioClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    static final String DEFAULT_REGION = "us-east-1";

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        var builder = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey());
        if (StringUtils.isNotBlank(properties.region())) {
            builder.region(properties.region());
        }
        return builder.build();
    }

    @Bean
    public PresignMinioClient presignMinioClient(StorageProperties properties) {
        var client = MinioClient.builder()
                .endpoint(StringUtils.defaultIfBlank(properties.publicEndpoint(), properties.endpoint()))
                .credentials(properties.accessKey(), properties.secretKey())
                .region(StringUtils.defaultIfBlank(properties.region(), DEFAULT_REGION))
                .build();
        return new PresignMinioClient(client);
    }
}
