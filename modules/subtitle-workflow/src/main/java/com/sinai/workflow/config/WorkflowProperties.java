package com.sinai.workflow.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.workflow")
public record WorkflowProperties(Duration downloadUrlExpiry, Path tempDirectory, Path uploadDirectory) {

    public WorkflowProperties {
        if (Objects.isNull(downloadUrlExpiry)) {
            downloadUrlExpiry = Duration.ofHours(1);
        }
        if (Objects.isNull(tempDirectory)) {
            tempDirectory = Path.of(System.getProperty("java.io.tmpdir"), "sin-ai");
        }
        tempDirectory = tempDirectory.toAbsolutePath().normalize();
        if (Objects.isNull(uploadDirectory)) {
            uploadDirectory = tempDirectory.resolve("uploads");
        }
        uploadDirectory = uploadDirectory.toAbsolutePath().normalize();
        if (!uploadDirectory.startsWith(tempDirectory) || uploadDirectory.equals(tempDirectory)) {
            throw new IllegalArgumentException("uploadDirectory must be inside tempDirectory: " + uploadDirectory);
        }
    }
}
