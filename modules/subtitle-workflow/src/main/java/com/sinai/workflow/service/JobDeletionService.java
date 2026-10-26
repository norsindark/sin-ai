package com.sinai.workflow.service;

import com.sinai.common.exception.SinAiException;
import com.sinai.storage.api.ObjectStorage;
import com.sinai.storage.config.StorageProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDeletionService {

    private final JobLifecycleService lifecycle;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;

    public void deleteJob(UUID jobId) {
        var deletedJob = lifecycle.deleteJob(jobId);
        deleteQuietly(deletedJob.videoBucket(), deletedJob.videoKey());
        var processedBucket = storageProperties.processedBucket();
        deleteQuietly(processedBucket, JobPipeline.outputVideoKey(jobId));
        for (var language : deletedJob.subtitleLanguages()) {
            deleteQuietly(processedBucket, JobPipeline.subtitleKey(jobId, language));
        }
        log.info("Deleted job {} and its stored files", jobId);
    }

    private void deleteQuietly(String bucket, String key) {
        try {
            objectStorage.delete(bucket, key);
        } catch (SinAiException e) {
            log.warn("Cannot delete {}/{} of a deleted job", bucket, key, e);
        }
    }
}
