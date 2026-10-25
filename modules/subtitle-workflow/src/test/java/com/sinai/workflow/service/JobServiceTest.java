package com.sinai.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.common.util.TempFileManager;
import com.sinai.storage.api.ObjectStorage;
import com.sinai.storage.config.StorageProperties;
import com.sinai.workflow.config.WorkflowProperties;
import com.sinai.workflow.model.entity.SubtitleJob;
import com.sinai.workflow.model.entity.TranslationTask;
import com.sinai.workflow.model.request.CreateJobRequest;
import com.sinai.workflow.repository.SubtitleJobRepository;
import com.sinai.workflow.repository.TranslationTaskRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class JobServiceTest {

    private static final byte[] MP4_BYTES = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 1};

    @TempDir
    Path tempDir;

    private SubtitleJobRepository jobRepository;
    private TranslationTaskRepository taskRepository;
    private ObjectStorage objectStorage;
    private Path workspaceBase;
    private JobService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(SubtitleJobRepository.class);
        taskRepository = mock(TranslationTaskRepository.class);
        objectStorage = mock(ObjectStorage.class);
        workspaceBase = tempDir.resolve("workspaces");
        var storageProperties = new StorageProperties("http://localhost:9000", "key", "secret", "raw-videos",
                "processed-videos", "us-east-1", null);
        service = new JobService(jobRepository, taskRepository, objectStorage, storageProperties,
                new WorkflowProperties(null), new TempFileManager(workspaceBase));
        when(jobRepository.save(any(SubtitleJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createJobUploadsVideoThenPersistsPendingJobWithTasks() throws Exception {
        var uploadedBytes = new ArrayList<byte[]>();
        doAnswer(invocation -> {
            uploadedBytes.add(Files.readAllBytes(invocation.getArgument(2, Path.class)));
            return null;
        }).when(objectStorage).upload(anyString(), anyString(), any(Path.class), anyString());

        var response = service.createJob(new CreateJobRequest(video("clip.mp4", MP4_BYTES), "EN",
                List.of("vi", " ja ")));

        var keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).upload(eq("raw-videos"), keyCaptor.capture(), any(Path.class), eq("video/mp4"));
        assertThat(keyCaptor.getValue()).matches("videos/[0-9a-f-]{36}/source\\.mp4");
        assertThat(uploadedBytes).singleElement().isEqualTo(MP4_BYTES);
        var jobCaptor = ArgumentCaptor.forClass(SubtitleJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        var job = jobCaptor.getValue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getSourceLanguage()).isEqualTo(Language.ENGLISH);
        assertThat(job.getVideo().getStorageKey()).isEqualTo(keyCaptor.getValue());
        assertThat(job.getVideo().getOriginalFilename()).isEqualTo("clip.mp4");
        assertThat(job.getVideo().getSizeBytes()).isEqualTo(MP4_BYTES.length);
        assertThat(job.getTranslationTasks())
                .extracting(TranslationTask::getTargetLanguage, TranslationTask::getStatus)
                .containsExactly(
                        tuple(Language.VIETNAMESE, JobStatus.PENDING),
                        tuple(Language.JAPANESE, JobStatus.PENDING));
        assertThat(response.sourceLanguage()).isEqualTo("en");
        assertThat(response.translations()).extracting(t -> t.targetLanguage()).containsExactly("ja", "vi");
        assertThat(response.translations()).allSatisfy(t -> assertThat(t.subtitleUrl()).isNull());
        assertThat(workspaceBase).isEmptyDirectory();
    }

    @Test
    void createJobDoesNotPersistWhenUploadFails() {
        doThrow(new SinAiException(ErrorCode.STORAGE_ERROR))
                .when(objectStorage).upload(anyString(), anyString(), any(Path.class), anyString());

        assertThatThrownBy(() -> service.createJob(new CreateJobRequest(video("clip.mp4", MP4_BYTES), "en",
                List.of("vi"))))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORAGE_ERROR));
        verify(jobRepository, never()).save(any());
        assertThat(workspaceBase).isEmptyDirectory();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "clip.txt | en | vi    | UNSUPPORTED_FILE_TYPE",
            "clip     | en | vi    | UNSUPPORTED_FILE_TYPE",
            "clip.mp4 | xx | vi    | UNSUPPORTED_LANGUAGE",
            "clip.mp4 | en | xx    | UNSUPPORTED_LANGUAGE",
            "clip.mp4 | en | en    | INVALID_ARGUMENT",
            "clip.mp4 | en | vi,vi | INVALID_ARGUMENT",
            "clip.mp4 | '' | vi    | UNSUPPORTED_LANGUAGE"})
    void createJobRejectsInvalidInputBeforeTouchingStorage(String filename, String source, String targets,
            ErrorCode expected) {
        var request = new CreateJobRequest(video(filename, MP4_BYTES), source, List.of(targets.split(",")));

        assertThatThrownBy(() -> service.createJob(request))
                .isInstanceOfSatisfying(SinAiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        verifyNoInteractions(objectStorage);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void createJobRejectsMissingTargetsAndEmptyFile() {
        assertThatThrownBy(() -> service.createJob(new CreateJobRequest(video("clip.mp4", MP4_BYTES), "en",
                List.of())))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.createJob(new CreateJobRequest(video("clip.mp4", new byte[0]), "en",
                List.of("vi"))))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.createJob(new CreateJobRequest(null, "en", List.of("vi"))))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        verifyNoInteractions(objectStorage);
    }

    @Test
    void createJobRejectsContentWithoutMp4SignatureAndCleansWorkspace() {
        var request = new CreateJobRequest(video("clip.mp4", "plain text, not a video".getBytes(
                StandardCharsets.UTF_8)), "en", List.of("vi"));

        assertThatThrownBy(() -> service.createJob(request))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
        verifyNoInteractions(objectStorage);
        assertThat(workspaceBase).isEmptyDirectory();
    }

    @Test
    void getJobFailsWithJobNotFound() {
        var id = UUID.randomUUID();
        when(jobRepository.findWithDetailsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(id))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND));
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void listJobsRejectsInvalidPaging(int page, int size) {
        assertThatThrownBy(() -> service.listJobs(page, size))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        verifyNoInteractions(jobRepository, taskRepository);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "clip.mp4              | clip.mp4",
            "C:\\videos\\clip.mp4 | clip.mp4",
            "../../etc/clip.mp4    | clip.mp4",
            "'  spaced.mp4 '       | spaced.mp4"})
    void sanitizeFilenameStripsDirectories(String raw, String expected) {
        assertThat(JobService.sanitizeFilename(raw)).isEqualTo(expected);
    }

    @Test
    void sanitizeFilenameKeepsExtensionWhenTruncating() {
        var longName = "a".repeat(400) + ".mp4";

        var sanitized = JobService.sanitizeFilename(longName);

        assertThat(sanitized).hasSize(JobService.MAX_FILENAME_LENGTH).endsWith(".mp4");
    }

    private static MockMultipartFile video(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }
}
