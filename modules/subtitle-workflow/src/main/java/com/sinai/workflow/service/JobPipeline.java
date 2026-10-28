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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPipeline {

    static final String SOURCE_VIDEO_NAME = "source." + FileType.VIDEO.getExtension();
    static final String AUDIO_NAME = "audio." + FileType.AUDIO.getExtension();
    static final String OUTPUT_VIDEO_NAME = "output." + FileType.VIDEO.getExtension();
    static final String JOB_OBSERVATION = "sinai.job";
    static final String STAGE_OBSERVATION = "sinai.job.stage";
    static final String JOB_ID_MDC_KEY = "jobId";
    private static final Clock CLOCK = Clock.systemUTC();

    private final JobLifecycleService lifecycle;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;
    private final TempFileManager tempFileManager;
    private final AudioExtractor audioExtractor;
    private final SpeechTranscriber speechTranscriber;
    private final SubtitleTranslator subtitleTranslator;
    private final SubtitleWriter subtitleWriter;
    private final SubtitleMerger subtitleMerger;
    private final ObservationRegistry observationRegistry;

    public void process(ClaimedJob job) {
        var targets = job.tasks().stream().map(task -> task.targetLanguage().getCode()).toList();
        var observation = Observation.createNotStarted(JOB_OBSERVATION, observationRegistry)
                .contextualName("process job")
                .lowCardinalityKeyValue("source.language", job.sourceLanguage().getCode())
                .highCardinalityKeyValue("job.id", job.id().toString())
                .highCardinalityKeyValue("target.languages", String.join(",", targets))
                .start();
        TempWorkspace workspace = null;
        try (var scope = observation.openScope(); var mdc = MDC.putCloseable(JOB_ID_MDC_KEY, job.id().toString())) {
            try {
                workspace = tempFileManager.createWorkspace(job.id());
                run(job, workspace);
                observation.lowCardinalityKeyValue("outcome", "COMPLETED");
            } catch (SinAiException e) {
                log.error("Job {} failed with {}", job.id(), e.getErrorCode(), e);
                observation.lowCardinalityKeyValue("outcome", e.getErrorCode().name()).error(e);
                lifecycle.fail(job.id(), e.getErrorCode());
            } catch (RuntimeException e) {
                log.error("Job {} failed unexpectedly", job.id(), e);
                observation.lowCardinalityKeyValue("outcome", ErrorCode.INTERNAL_ERROR.name()).error(e);
                lifecycle.fail(job.id(), ErrorCode.INTERNAL_ERROR);
            } finally {
                closeQuietly(job.id(), workspace);
            }
        } finally {
            observation.stop();
        }
    }

    private void run(ClaimedJob job, TempWorkspace workspace) {
        var sourceVideo = workspace.resolve(SOURCE_VIDEO_NAME);
        stage("download", () -> objectStorage.download(job.videoBucket(), job.videoKey(), sourceVideo));
        var audio = workspace.resolve(AUDIO_NAME);
        stage("extract-audio", () -> audioExtractor.extractAudio(sourceVideo, audio));
        var transcriptionProgress = new ThrottledProgress(
                percent -> lifecycle.updateTranscriptionProgress(job.id(), percent), CLOCK);
        var segments = stageResult("transcribe",
                () -> speechTranscriber.transcribe(audio, job.sourceLanguage(), transcriptionProgress));
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
        stage("merge", () -> subtitleMerger.merge(sourceVideo, tracks, outputVideo));
        var outputKey = outputVideoKey(job.id());
        stage("upload", () -> objectStorage.upload(storageProperties.processedBucket(), outputKey, outputVideo,
                FileType.VIDEO.getContentType()));
        lifecycle.complete(job.id(), outputKey);
        log.info("Job {} completed with {} subtitle tracks", job.id(), tracks.size());
    }

    private Optional<SubtitleTrack> translate(ClaimedJob job, ClaimedJob.Task task, List<SubtitleSegment> segments,
            TempWorkspace workspace) {
        lifecycle.startTask(task.id());
        var observation = Observation.createNotStarted(STAGE_OBSERVATION, observationRegistry)
                .contextualName("translate " + task.targetLanguage().getCode())
                .lowCardinalityKeyValue("stage", "translate")
                .lowCardinalityKeyValue("target.language", task.targetLanguage().getCode())
                .start();
        try (var scope = observation.openScope()) {
            var taskProgress = new ThrottledProgress(
                    percent -> lifecycle.updateTaskProgress(task.id(), percent), CLOCK);
            var translated = subtitleTranslator.translate(segments, job.sourceLanguage(), task.targetLanguage(),
                    taskProgress);
            var track = publishSubtitle(job.id(), task.targetLanguage(), translated, workspace);
            lifecycle.completeTask(task.id(), subtitleKey(job.id(), task.targetLanguage()));
            return Optional.of(track);
        } catch (SinAiException e) {
            log.warn("Job {} translation to {} failed with {}", job.id(), task.targetLanguage().getCode(),
                    e.getErrorCode(), e);
            observation.error(e);
            lifecycle.failTask(task.id(), e.getErrorCode());
            return Optional.empty();
        } finally {
            observation.stop();
        }
    }

    private void stage(String name, Runnable action) {
        stageObservation(name).observe(action);
    }

    private <T> T stageResult(String name, Supplier<T> action) {
        return stageObservation(name).observe(action);
    }

    private Observation stageObservation(String name) {
        return Observation.createNotStarted(STAGE_OBSERVATION, observationRegistry)
                .contextualName(name)
                .lowCardinalityKeyValue("stage", name);
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
