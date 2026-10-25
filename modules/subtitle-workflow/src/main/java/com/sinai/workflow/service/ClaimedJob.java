package com.sinai.workflow.service;

import com.sinai.common.constant.Language;
import com.sinai.workflow.model.entity.SubtitleJob;
import java.util.List;
import java.util.UUID;

public record ClaimedJob(UUID id, String videoBucket, String videoKey, Language sourceLanguage,
        List<Task> tasks) {

    public ClaimedJob {
        tasks = List.copyOf(tasks);
    }

    static ClaimedJob from(SubtitleJob job) {
        var tasks = job.getTranslationTasks().stream()
                .map(task -> new Task(task.getId(), task.getTargetLanguage()))
                .toList();
        var video = job.getVideo();
        return new ClaimedJob(job.getId(), video.getStorageBucket(), video.getStorageKey(), job.getSourceLanguage(),
                tasks);
    }

    public record Task(UUID id, Language targetLanguage) {
    }
}
