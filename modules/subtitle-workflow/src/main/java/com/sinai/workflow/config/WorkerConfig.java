package com.sinai.workflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "sinai.workflow.worker.enabled", matchIfMissing = true)
public class WorkerConfig {
}
