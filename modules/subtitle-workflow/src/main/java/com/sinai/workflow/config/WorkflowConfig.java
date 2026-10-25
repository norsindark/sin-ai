package com.sinai.workflow.config;

import com.sinai.common.util.TempFileManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowConfig {

    @Bean
    public TempFileManager tempFileManager() {
        return new TempFileManager();
    }
}
