package com.sinai.workflow.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.workflow")
public record WorkflowProperties(Duration downloadUrlExpiry) {

    public WorkflowProperties {
        if (Objects.isNull(downloadUrlExpiry)) {
            downloadUrlExpiry = Duration.ofHours(1);
        }
    }
}
