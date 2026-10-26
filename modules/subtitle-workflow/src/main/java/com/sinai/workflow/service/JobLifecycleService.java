package com.sinai.workflow.service;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.workflow.model.entity.SubtitleJob;
import com.sinai.workflow.model.entity.TranslationTask;
import com.sinai.workflow.repository.SubtitleJobRepository;
import com.sinai.workflow.repository.TranslationTaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobLifecycleService {

    private final SubtitleJobRepository jobRepository;
    private final TranslationTaskRepository taskRepository;

    @Transactional
    public Optional<ClaimedJob> claimNextPending() {
        return jobRepository.lockNextPendingId()
                .map(id -> {
                    var job = loadJob(UUID.fromString(id));
                    job.start();
                    return ClaimedJob.from(job);
                });
    }

    @Transactional
    public List<UUID> failInterruptedJobs() {
        var interrupted = jobRepository.findAllByStatus(JobStatus.PROCESSING);
        interrupted.forEach(job -> job.fail(ErrorCode.JOB_INTERRUPTED));
        return interrupted.stream().map(SubtitleJob::getId).toList();
    }

    @Transactional
    public void attachSourceSubtitle(UUID jobId, String subtitleKey) {
        loadJob(jobId).attachSourceSubtitle(subtitleKey);
    }

    @Transactional
    public void startTask(UUID taskId) {
        loadTask(taskId).start();
    }

    @Transactional
    public void completeTask(UUID taskId, String subtitleKey) {
        loadTask(taskId).complete(subtitleKey);
    }

    @Transactional
    public void failTask(UUID taskId, ErrorCode failure) {
        loadTask(taskId).fail(failure.getDefaultMessage());
    }

    @Transactional
    public void complete(UUID jobId, String outputVideoKey) {
        loadJob(jobId).complete(outputVideoKey);
    }

    @Transactional
    public void fail(UUID jobId, ErrorCode failure) {
        loadJob(jobId).fail(failure);
    }

    @Transactional
    public DeletedJob deleteJob(UUID jobId) {
        var job = jobRepository.findForUpdateById(jobId)
                .orElseThrow(() -> new SinAiException(ErrorCode.JOB_NOT_FOUND, "Job not found: " + jobId));
        if (job.getStatus() == JobStatus.PROCESSING) {
            throw new SinAiException(ErrorCode.JOB_IN_PROGRESS, "Job is still processing: " + jobId);
        }
        var video = job.getVideo();
        var subtitleLanguages = new ArrayList<Language>();
        subtitleLanguages.add(job.getSourceLanguage());
        job.getTranslationTasks().stream()
                .map(TranslationTask::getTargetLanguage)
                .forEach(subtitleLanguages::add);
        var deletedJob = new DeletedJob(jobId, video.getStorageBucket(), video.getStorageKey(),
                List.copyOf(subtitleLanguages));
        jobRepository.deleteJobRow(jobId.toString());
        jobRepository.deleteVideoRow(video.getId().toString());
        return deletedJob;
    }

    private SubtitleJob loadJob(UUID jobId) {
        return jobRepository.findWithDetailsById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
    }

    private TranslationTask loadTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Translation task not found: " + taskId));
    }
}
