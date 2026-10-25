package com.sinai.workflow.service;

import com.sinai.common.constant.FileType;
import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.common.util.TempFileManager;
import com.sinai.storage.api.ObjectStorage;
import com.sinai.storage.config.StorageProperties;
import com.sinai.workflow.config.WorkflowProperties;
import com.sinai.workflow.model.entity.SubtitleJob;
import com.sinai.workflow.model.entity.TranslationTask;
import com.sinai.workflow.model.entity.Video;
import com.sinai.workflow.model.request.CreateJobRequest;
import com.sinai.workflow.model.response.JobResponse;
import com.sinai.workflow.model.response.JobSummaryResponse;
import com.sinai.workflow.model.response.PageResponse;
import com.sinai.workflow.model.response.TranslationTaskResponse;
import com.sinai.workflow.model.response.VideoResponse;
import com.sinai.workflow.repository.SubtitleJobRepository;
import com.sinai.workflow.repository.TranslationTaskRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class JobService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_FILENAME_LENGTH = 255;
    private static final String SOURCE_VIDEO_NAME = "source." + FileType.VIDEO.getExtension();
    private static final byte[] MP4_SIGNATURE = {'f', 't', 'y', 'p'};
    private static final int MP4_SIGNATURE_OFFSET = 4;

    private final SubtitleJobRepository jobRepository;
    private final TranslationTaskRepository taskRepository;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;
    private final WorkflowProperties workflowProperties;
    private final TempFileManager tempFileManager;

    public JobResponse createJob(CreateJobRequest request) {
        var file = request.file();
        var originalFilename = validateVideo(file);
        var sourceLanguage = Language.fromCode(request.sourceLanguage());
        var targetLanguages = parseTargetLanguages(sourceLanguage, request.targetLanguages());
        var videoId = UUID.randomUUID();
        var storageKey = "videos/" + videoId + "/" + SOURCE_VIDEO_NAME;
        var rawBucket = storageProperties.rawBucket();
        try (var workspace = tempFileManager.createWorkspace(videoId)) {
            var localVideo = workspace.resolve(SOURCE_VIDEO_NAME);
            transfer(file, localVideo);
            verifyMp4Signature(localVideo);
            objectStorage.upload(rawBucket, storageKey, localVideo, FileType.VIDEO.getContentType());
        }
        var video = new Video(videoId, originalFilename, FileType.VIDEO.getContentType(), file.getSize(),
                rawBucket, storageKey);
        var job = jobRepository.save(SubtitleJob.create(video, sourceLanguage, targetLanguages));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID id) {
        return jobRepository.findWithDetailsById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new SinAiException(ErrorCode.JOB_NOT_FOUND, "Job not found: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobSummaryResponse> listJobs(int page, int size) {
        if (page < 0) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        var jobs = jobRepository.findPageWithVideo(PageRequest.of(page, size, sort));
        var jobIds = jobs.getContent().stream().map(job -> job.getId().toString()).toArray(String[]::new);
        var targetsByJob = ArrayUtils.isEmpty(jobIds)
                ? Map.<UUID, List<String>>of()
                : taskRepository.findAllByJobIds(jobIds).stream()
                        .collect(Collectors.groupingBy(task -> task.getJob().getId(),
                                Collectors.mapping(task -> task.getTargetLanguage().getCode(), Collectors.toList())));
        var items = jobs.getContent().stream()
                .map(job -> new JobSummaryResponse(
                        job.getId(),
                        job.getStatus(),
                        job.getSourceLanguage().getCode(),
                        targetsByJob.getOrDefault(job.getId(), List.of()),
                        job.getVideo().getOriginalFilename(),
                        job.getCreatedAt(),
                        job.getUpdatedAt()))
                .toList();
        return new PageResponse<>(items, jobs.getNumber(), jobs.getSize(), jobs.getTotalElements(),
                jobs.getTotalPages());
    }

    private String validateVideo(MultipartFile file) {
        if (Objects.isNull(file) || file.isEmpty()) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "file must not be empty");
        }
        var originalFilename = sanitizeFilename(file.getOriginalFilename());
        var extension = StringUtils.substringAfterLast(originalFilename, ".");
        if (FileType.fromExtension(extension) != FileType.VIDEO) {
            throw new SinAiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Only ." + FileType.VIDEO.getExtension()
                    + " videos are accepted");
        }
        return originalFilename;
    }

    static String sanitizeFilename(String rawFilename) {
        var baseName = StringUtils.substringAfterLast(StringUtils.replaceChars(rawFilename, '\\', '/'), "/");
        var candidate = StringUtils.isBlank(baseName) ? StringUtils.trim(rawFilename) : StringUtils.trim(baseName);
        if (StringUtils.isBlank(candidate)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "file name must not be blank");
        }
        return StringUtils.right(candidate, MAX_FILENAME_LENGTH);
    }

    private static Set<Language> parseTargetLanguages(Language sourceLanguage, List<String> rawTargets) {
        if (CollectionUtils.isEmpty(rawTargets)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "targetLanguages must not be empty");
        }
        var targets = new LinkedHashSet<Language>();
        for (var rawTarget : rawTargets) {
            var target = Language.fromCode(rawTarget);
            if (target == sourceLanguage) {
                throw new SinAiException(ErrorCode.INVALID_ARGUMENT,
                        "Target language must differ from source language: " + target.getCode());
            }
            if (!targets.add(target)) {
                throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Duplicate target language: " + target.getCode());
            }
        }
        return targets;
    }

    private static void transfer(MultipartFile file, Path target) {
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.WORKSPACE_IO_ERROR, "Cannot store uploaded file", e);
        }
    }

    static void verifyMp4Signature(Path video) {
        var header = new byte[MP4_SIGNATURE_OFFSET + MP4_SIGNATURE.length];
        try (var input = Files.newInputStream(video)) {
            var read = input.readNBytes(header, 0, header.length);
            var signature = Arrays.copyOfRange(header, MP4_SIGNATURE_OFFSET, header.length);
            if (read < header.length || !Arrays.equals(signature, MP4_SIGNATURE)) {
                throw new SinAiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "File content is not a valid MP4 video");
            }
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.WORKSPACE_IO_ERROR, "Cannot read uploaded file", e);
        }
    }

    private JobResponse toResponse(SubtitleJob job) {
        var video = job.getVideo();
        var translations = job.getTranslationTasks().stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(TranslationTaskResponse::targetLanguage))
                .toList();
        return new JobResponse(
                job.getId(),
                job.getStatus(),
                job.getSourceLanguage().getCode(),
                new VideoResponse(video.getId(), video.getOriginalFilename(), video.getSizeBytes()),
                presignProcessed(job.getSourceSubtitleKey()),
                presignProcessed(job.getOutputVideoKey()),
                EnumUtils.getEnum(ErrorCode.class, job.getErrorCode()),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                translations);
    }

    private TranslationTaskResponse toResponse(TranslationTask task) {
        return new TranslationTaskResponse(
                task.getTargetLanguage().getCode(),
                task.getStatus(),
                presignProcessed(task.getSubtitleKey()),
                task.getErrorMessage());
    }

    private String presignProcessed(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        return objectStorage.presignedGetUrl(storageProperties.processedBucket(), key,
                workflowProperties.downloadUrlExpiry());
    }
}
