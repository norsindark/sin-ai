package com.sinai.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sinai.ai.api.ProgressListener;
import com.sinai.ai.api.SpeechTranscriber;
import com.sinai.ai.api.SubtitleTranslator;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.common.util.TempFileManager;
import com.sinai.media.api.AudioExtractor;
import com.sinai.media.api.SubtitleMerger;
import com.sinai.media.api.SubtitleTrack;
import com.sinai.media.api.SubtitleWriter;
import com.sinai.storage.api.ObjectStorage;
import com.sinai.storage.config.StorageProperties;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class JobPipelineTest {

    private static final List<SubtitleSegment> SEGMENTS = List.of(
            new SubtitleSegment(1, 0, 1000, "Hello"),
            new SubtitleSegment(2, 1000, 2000, "Goodbye"));

    @TempDir
    Path tempDir;

    private JobLifecycleService lifecycle;
    private ObjectStorage objectStorage;
    private AudioExtractor audioExtractor;
    private SpeechTranscriber speechTranscriber;
    private SubtitleTranslator subtitleTranslator;
    private SubtitleWriter subtitleWriter;
    private SubtitleMerger subtitleMerger;
    private Path workspaceBase;
    private JobPipeline pipeline;
    private ClaimedJob job;
    private TestObservationRegistry observations;

    @BeforeEach
    void setUp() {
        observations = TestObservationRegistry.create();
        lifecycle = mock(JobLifecycleService.class);
        objectStorage = mock(ObjectStorage.class);
        audioExtractor = mock(AudioExtractor.class);
        speechTranscriber = mock(SpeechTranscriber.class);
        subtitleTranslator = mock(SubtitleTranslator.class);
        subtitleWriter = mock(SubtitleWriter.class);
        subtitleMerger = mock(SubtitleMerger.class);
        workspaceBase = tempDir.resolve("workspaces");
        var storageProperties = new StorageProperties("http://localhost:9000", "key", "secret", "raw-videos",
                "processed-videos", "us-east-1", null);
        pipeline = new JobPipeline(lifecycle, objectStorage, storageProperties, new TempFileManager(workspaceBase),
                audioExtractor, speechTranscriber, subtitleTranslator, subtitleWriter, subtitleMerger, observations);
        job = new ClaimedJob(UUID.randomUUID(), "raw-videos", "videos/abc/source.mp4", Language.ENGLISH, List.of(
                new ClaimedJob.Task(UUID.randomUUID(), Language.VIETNAMESE),
                new ClaimedJob.Task(UUID.randomUUID(), Language.JAPANESE)));
        when(speechTranscriber.transcribe(any(Path.class), eq(Language.ENGLISH), any(ProgressListener.class)))
                .thenReturn(SEGMENTS);
        when(subtitleTranslator.translate(eq(SEGMENTS), eq(Language.ENGLISH), any(Language.class),
                any(ProgressListener.class)))
                .thenReturn(SEGMENTS);
    }

    @Test
    void recordsTranscriptionAndTranslationProgress() {
        when(speechTranscriber.transcribe(any(Path.class), eq(Language.ENGLISH), any(ProgressListener.class)))
                .thenAnswer(invocation -> {
                    var listener = invocation.getArgument(2, ProgressListener.class);
                    listener.onProgress(0);
                    listener.onProgress(40);
                    listener.onProgress(100);
                    return SEGMENTS;
                });
        when(subtitleTranslator.translate(eq(SEGMENTS), eq(Language.ENGLISH), any(Language.class),
                any(ProgressListener.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(3, ProgressListener.class).onProgress(100);
                    return SEGMENTS;
                });

        pipeline.process(job);

        verify(lifecycle).updateTranscriptionProgress(job.id(), 0);
        verify(lifecycle, never()).updateTranscriptionProgress(job.id(), 40);
        verify(lifecycle).updateTranscriptionProgress(job.id(), 100);
        for (var task : job.tasks()) {
            verify(lifecycle).updateTaskProgress(task.id(), 100);
        }
    }

    @Test
    void processesEveryStageAndPublishesMultiLanguageVideo() {
        pipeline.process(job);

        var prefix = "jobs/" + job.id() + "/";
        var order = inOrder(objectStorage, audioExtractor, speechTranscriber, subtitleMerger, lifecycle);
        order.verify(objectStorage).download(eq("raw-videos"), eq("videos/abc/source.mp4"), any(Path.class));
        order.verify(audioExtractor).extractAudio(any(Path.class), any(Path.class));
        order.verify(speechTranscriber)
                .transcribe(any(Path.class), eq(Language.ENGLISH), any(ProgressListener.class));
        order.verify(lifecycle).attachSourceSubtitle(job.id(), prefix + "subtitles/en.srt");
        order.verify(subtitleMerger).merge(any(Path.class), any(), any(Path.class));
        order.verify(objectStorage).upload(eq("processed-videos"), eq(prefix + "output.mp4"), any(Path.class),
                eq("video/mp4"));
        order.verify(lifecycle).complete(job.id(), prefix + "output.mp4");
        for (var code : List.of("en", "vi", "ja")) {
            verify(objectStorage).upload(eq("processed-videos"), eq(prefix + "subtitles/" + code + ".srt"),
                    any(Path.class), eq("application/x-subrip"));
        }
        for (var task : job.tasks()) {
            verify(lifecycle).startTask(task.id());
            verify(lifecycle).completeTask(task.id(),
                    prefix + "subtitles/" + task.targetLanguage().getCode() + ".srt");
        }
        assertThat(mergedLanguages()).containsExactly(Language.ENGLISH, Language.VIETNAMESE, Language.JAPANESE);
        verify(lifecycle, never()).fail(any(), any());
        assertThat(workspaceBase).isEmptyDirectory();
        TestObservationRegistryAssert.assertThat(observations)
                .hasObservationWithNameEqualTo(JobPipeline.JOB_OBSERVATION).that()
                .hasLowCardinalityKeyValue("outcome", "COMPLETED")
                .hasHighCardinalityKeyValue("job.id", job.id().toString())
                .hasBeenStopped();
        TestObservationRegistryAssert.assertThat(observations)
                .hasNumberOfObservationsWithNameEqualTo(JobPipeline.STAGE_OBSERVATION, 7);
    }

    @Test
    void failedTranslationIsIsolatedToItsTask() {
        when(subtitleTranslator.translate(eq(SEGMENTS), eq(Language.ENGLISH), eq(Language.JAPANESE),
                any(ProgressListener.class)))
                .thenThrow(new SinAiException(ErrorCode.TRANSLATION_ERROR, "Ollama is down"));

        pipeline.process(job);

        var japanese = job.tasks().get(1);
        verify(lifecycle).failTask(japanese.id(), ErrorCode.TRANSLATION_ERROR);
        verify(lifecycle, never()).completeTask(eq(japanese.id()), anyString());
        verify(lifecycle).completeTask(eq(job.tasks().get(0).id()), anyString());
        assertThat(mergedLanguages()).containsExactly(Language.ENGLISH, Language.VIETNAMESE);
        verify(lifecycle).complete(eq(job.id()), anyString());
        assertThat(workspaceBase).isEmptyDirectory();
    }

    @Test
    void criticalStageFailureFailsJobWithItsErrorCode() {
        doThrow(new SinAiException(ErrorCode.MEDIA_PROCESSING_ERROR, "ffmpeg exited with code 1"))
                .when(audioExtractor).extractAudio(any(Path.class), any(Path.class));

        pipeline.process(job);

        verify(lifecycle).fail(job.id(), ErrorCode.MEDIA_PROCESSING_ERROR);
        verify(lifecycle, never()).complete(any(), any());
        TestObservationRegistryAssert.assertThat(observations)
                .hasObservationWithNameEqualTo(JobPipeline.JOB_OBSERVATION).that()
                .hasLowCardinalityKeyValue("outcome", ErrorCode.MEDIA_PROCESSING_ERROR.name())
                .hasError();
        verifyNoInteractions(speechTranscriber, subtitleTranslator, subtitleMerger);
        assertThat(workspaceBase).isEmptyDirectory();
    }

    @Test
    void emptyTranscriptFailsWithNoSpeechDetected() {
        when(speechTranscriber.transcribe(any(Path.class), eq(Language.ENGLISH), any(ProgressListener.class)))
                .thenReturn(List.of());

        pipeline.process(job);

        verify(lifecycle).fail(job.id(), ErrorCode.NO_SPEECH_DETECTED);
        verify(objectStorage, never()).upload(anyString(), anyString(), any(Path.class), anyString());
        verifyNoInteractions(subtitleWriter, subtitleTranslator, subtitleMerger);
        assertThat(workspaceBase).isEmptyDirectory();
    }

    @Test
    void unexpectedExceptionFailsJobWithInternalError() {
        when(speechTranscriber.transcribe(any(Path.class), eq(Language.ENGLISH), any(ProgressListener.class)))
                .thenThrow(new IllegalStateException("boom"));

        pipeline.process(job);

        verify(lifecycle).fail(job.id(), ErrorCode.INTERNAL_ERROR);
        assertThat(workspaceBase).isEmptyDirectory();
    }

    @SuppressWarnings("unchecked")
    private List<Language> mergedLanguages() {
        var captor = ArgumentCaptor.forClass(List.class);
        verify(subtitleMerger).merge(any(Path.class), captor.capture(), any(Path.class));
        return ((List<SubtitleTrack>) captor.getValue()).stream().map(SubtitleTrack::language).toList();
    }
}
