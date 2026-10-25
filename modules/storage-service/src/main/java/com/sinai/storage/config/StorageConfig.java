package com.sinai.storage.config;

import io.minio.MinioClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

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
}
