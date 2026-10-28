package com.sinai.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.workflow.controller.JobController;
import com.sinai.workflow.model.request.CreateJobRequest;
import com.sinai.workflow.model.request.RenameJobRequest;
import com.sinai.workflow.model.response.JobResponse;
import com.sinai.workflow.model.response.PageResponse;
import com.sinai.workflow.model.response.VideoResponse;
import com.sinai.workflow.service.JobDeletionService;
import com.sinai.workflow.service.JobService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest(JobController.class)
@Import(GlobalExceptionHandler.class)
class JobApiWebMvcTest {

    private static final MockMultipartFile VIDEO = new MockMultipartFile("file", "clip.mp4", "video/mp4",
            new byte[] {1, 2, 3});

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobDeletionService jobDeletionService;

    @Test
    void createJobBindsMultipartFieldsAndReturnsCreated() throws Exception {
        var job = sampleJob();
        when(jobService.createJob(any())).thenReturn(job);

        mockMvc.perform(multipart("/api/v1/jobs")
                        .file(VIDEO)
                        .param("sourceLanguage", "en")
                        .param("targetLanguages", "vi", "ja"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/jobs/" + job.id()))
                .andExpect(jsonPath("$.id").value(job.id().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.translations").isEmpty());

        var captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(jobService).createJob(captor.capture());
        assertThat(captor.getValue().sourceLanguage()).isEqualTo("en");
        assertThat(captor.getValue().targetLanguages()).containsExactly("vi", "ja");
        assertThat(captor.getValue().file().getOriginalFilename()).isEqualTo("clip.mp4");
    }

    @Test
    void clientErrorFromServiceKeepsCodeAndMessage() throws Exception {
        when(jobService.createJob(any()))
                .thenThrow(new SinAiException(ErrorCode.UNSUPPORTED_LANGUAGE, "Unsupported language: xx"));

        mockMvc.perform(multipart("/api/v1/jobs").file(VIDEO).param("sourceLanguage", "xx")
                        .param("targetLanguages", "vi"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_LANGUAGE"))
                .andExpect(jsonPath("$.message").value("Unsupported language: xx"));
    }

    @Test
    void serverErrorFromServiceHidesInternalMessage() throws Exception {
        when(jobService.createJob(any()))
                .thenThrow(new SinAiException(ErrorCode.STORAGE_ERROR, "Failed to upload raw-videos/secret-key"));

        mockMvc.perform(multipart("/api/v1/jobs").file(VIDEO).param("sourceLanguage", "en")
                        .param("targetLanguages", "vi"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"))
                .andExpect(jsonPath("$.message").value("Storage error"));
    }

    @Test
    void unexpectedErrorMapsToInternalError() throws Exception {
        when(jobService.getJob(any())).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/v1/jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal error"));
    }

    @Test
    void uploadSizeExceededMapsToFileTooLarge() throws Exception {
        when(jobService.createJob(any())).thenThrow(new MaxUploadSizeExceededException(500L));

        mockMvc.perform(multipart("/api/v1/jobs").file(VIDEO).param("sourceLanguage", "en")
                        .param("targetLanguages", "vi"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void missingFieldsMapToInvalidArgumentWithDetail() throws Exception {
        mockMvc.perform(multipart("/api/v1/jobs").file(VIDEO).param("sourceLanguage", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("targetLanguages")));

        mockMvc.perform(multipart("/api/v1/jobs").param("sourceLanguage", "en").param("targetLanguages", "vi"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("file")));
    }

    @Test
    void malformedIdMapsToInvalidArgument() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void jobNotFoundMapsTo404() throws Exception {
        var id = UUID.randomUUID();
        when(jobService.getJob(id)).thenThrow(new SinAiException(ErrorCode.JOB_NOT_FOUND, "Job not found: " + id));

        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void unsupportedMethodKeepsStatusWithInvalidArgumentCode() throws Exception {
        mockMvc.perform(delete("/api/v1/jobs"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("DELETE")));
    }

    @Test
    void renameJobReadsJsonTitle() throws Exception {
        var job = sampleJob();
        when(jobService.renameJob(any(), any())).thenReturn(job);

        mockMvc.perform(patch("/api/v1/jobs/{id}", job.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Họp nhóm tuần 3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.title").value("clip"));

        var captor = ArgumentCaptor.forClass(RenameJobRequest.class);
        verify(jobService).renameJob(eq(job.id()), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Họp nhóm tuần 3");
    }

    @Test
    void renameJobRejectsInvalidTitle() throws Exception {
        when(jobService.renameJob(any(), any()))
                .thenThrow(new SinAiException(ErrorCode.INVALID_ARGUMENT, "title must not be blank"));

        mockMvc.perform(patch("/api/v1/jobs/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void deleteJobReturnsNoContent() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/jobs/{id}", id))
                .andExpect(status().isNoContent());

        verify(jobDeletionService).deleteJob(id);
    }

    @Test
    void deleteProcessingJobMapsToConflict() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new SinAiException(ErrorCode.JOB_IN_PROGRESS, "Job is still processing: " + id))
                .when(jobDeletionService).deleteJob(id);

        mockMvc.perform(delete("/api/v1/jobs/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_IN_PROGRESS"));
    }

    @Test
    void deleteUnknownJobMapsTo404() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new SinAiException(ErrorCode.JOB_NOT_FOUND, "Job not found: " + id))
                .when(jobDeletionService).deleteJob(id);

        mockMvc.perform(delete("/api/v1/jobs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void listUsesDefaultPaging() throws Exception {
        when(jobService.listJobs(0, 20)).thenReturn(new PageResponse<>(
                List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.size").value(20));
    }

    private static JobResponse sampleJob() {
        var now = Instant.parse("2026-10-25T00:00:00Z");
        return new JobResponse(UUID.randomUUID(), JobStatus.PENDING, "en",
                new VideoResponse(UUID.randomUUID(), "clip.mp4", "clip", 3), null, null, null, null, null, now, now, null,
                null,
                List.of());
    }
}
