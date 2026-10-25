package com.sinai.workflow.config;

import com.sinai.common.exception.SinAiException;
import com.sinai.common.util.TempFileManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

@Slf4j
@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowConfig {

    @Bean
    public TempFileManager tempFileManager(WorkflowProperties properties) {
        var manager = new TempFileManager(properties.tempDirectory());
        purgeLeftovers(manager, properties);
        return manager;
    }

    private static void purgeLeftovers(TempFileManager manager, WorkflowProperties properties) {
        try {
            var workspaces = manager.purgeStaleWorkspaces();
            var uploads = manager.purgeDirectoryContents(properties.uploadDirectory());
            if (!CollectionUtils.isEmpty(workspaces) || !CollectionUtils.isEmpty(uploads)) {
                log.warn("Purged {} stale workspaces and {} stale uploads from {}", workspaces.size(), uploads.size(),
                        properties.tempDirectory());
            }
        } catch (SinAiException e) {
            log.warn("Cannot fully purge temporary files in {}", properties.tempDirectory(), e);
        }
    }
}
