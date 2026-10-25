package com.sinai.workflow.service;

import com.sinai.ai.api.SpeechTranscriber;
import com.sinai.ai.api.SubtitleTranslator;
import com.sinai.common.constant.FileType;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.common.util.TempFileManager;
import com.sinai.common.util.TempWorkspace;
import com.sinai.media.api.AudioExtractor;
import com.sinai.media.api.SubtitleMerger;
import com.sinai.media.api.SubtitleTrack;
import com.sinai.media.api.SubtitleWriter;
import com.sinai.storage.api.ObjectStorage;
import com.sinai.storage.config.StorageProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPipeline {

    static final String SOURCE_VIDEO_NAME = "source." + FileType.VIDEO.getExtension();
    static final String AUDIO_NAME = "audio." + FileType.AUDIO.getExtension();
    static final String OUTPUT_VIDEO_NAME = "output." + FileType.VIDEO.getExtension();

    private final JobLifecycleService lifecycle;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;
    private final TempFileManager tempFileManager;
    private final AudioExtractor audioExtractor;
    private final SpeechTranscriber speechTranscriber;
    private final SubtitleTranslator subtitleTranslator;
    private final SubtitleWriter subtitleWriter;
    private final SubtitleMerger subtitleMerger;

    public void process(ClaimedJob job) {
        TempWorkspace workspace = null;
        try {
            workspace = tempFileManager.createWorkspace(job.id());
            run(job, workspace);
        } catch (SinAiException e) {
            log.error("Job {} failed with {}", job.id(), e.getErrorCode(), e);
            lifecycle.fail(job.id(), e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("Job {} failed unexpectedly", job.id(), e);
            lifecycle.fail(job.id(), ErrorCode.INTERNAL_ERROR);
        } finally {
            closeQuietly(job.id(), workspace);
        }
    }

    private void run(ClaimedJob job, TempWorkspace workspace) {
        var sourceVideo = workspace.resolve(SOURCE_VIDEO_NAME);
        objectStorage.download(job.videoBucket(), job.videoKey(), sourceVideo);
        var audio = workspace.resolve(AUDIO_NAME);
        audioExtractor.extractAudio(sourceVideo, audio);
        var segments = speechTranscriber.transcribe(audio, job.sourceLanguage());
        if (CollectionUtils.isEmpty(segments)) {
            throw new SinAiException(ErrorCode.NO_SPEECH_DETECTED);
        }
        var tracks = new ArrayList<SubtitleTrack>();
        var sourceTrack = publishSubtitle(job.id(), job.sourceLanguage(), segments, workspace);
        lifecycle.attachSourceSubtitle(job.id(), subtitleKey(job.id(), job.sourceLanguage()));
        tracks.add(sourceTrack);
        for (var task : job.tasks()) {
            translate(job, task, segments, workspace).ifPresent(tracks::add);
        }
        var outputVideo = workspace.resolve(OUTPUT_VIDEO_NAME);
        subtitleMerger.merge(sourceVideo, tracks, outputVideo);
        var outputKey = outputVideoKey(job.id());
        objectStorage.upload(storageProperties.processedBucket(), outputKey, outputVideo,
                FileType.VIDEO.getContentType());
        lifecycle.complete(job.id(), outputKey);
        log.info("Job {} completed with {} subtitle tracks", job.id(), tracks.size());
    }

    private Optional<SubtitleTrack> translate(ClaimedJob job, ClaimedJob.Task task, List<SubtitleSegment> segments,
            TempWorkspace workspace) {
        lifecycle.startTask(task.id());
        try {
            var translated = subtitleTranslator.translate(segments, job.sourceLanguage(), task.targetLanguage());
            var track = publishSubtitle(job.id(), task.targetLanguage(), translated, workspace);
            lifecycle.completeTask(task.id(), subtitleKey(job.id(), task.targetLanguage()));
            return Optional.of(track);
        } catch (SinAiException e) {
            log.warn("Job {} translation to {} failed with {}", job.id(), task.targetLanguage().getCode(),
                    e.getErrorCode(), e);
            lifecycle.failTask(task.id(), e.getErrorCode());
            return Optional.empty();
        }
    }

    private SubtitleTrack publishSubtitle(UUID jobId, Language language, List<SubtitleSegment> segments,
            TempWorkspace workspace) {
        var srtFile = workspace.resolve(language.getCode() + "." + FileType.SUBTITLE.getExtension());
        subtitleWriter.write(segments, srtFile);
        objectStorage.upload(storageProperties.processedBucket(), subtitleKey(jobId, language), srtFile,
                FileType.SUBTITLE.getContentType());
        return new SubtitleTrack(srtFile, language);
    }

    static String subtitleKey(UUID jobId, Language language) {
        return "jobs/" + jobId + "/subtitles/" + language.getCode() + "." + FileType.SUBTITLE.getExtension();
    }

    static String outputVideoKey(UUID jobId) {
        return "jobs/" + jobId + "/" + OUTPUT_VIDEO_NAME;
    }

    private static void closeQuietly(UUID jobId, TempWorkspace workspace) {
        if (Objects.isNull(workspace)) {
            return;
        }
        try {
            workspace.close();
        } catch (SinAiException e) {
            log.warn("Cannot clean up workspace of job {}", jobId, e);
        }
    }
}
