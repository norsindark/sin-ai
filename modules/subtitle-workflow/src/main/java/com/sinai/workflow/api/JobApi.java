package com.sinai.workflow.api;

import com.sinai.common.dto.ApiErrorResponse;
import com.sinai.workflow.model.request.CreateJobRequest;
import com.sinai.workflow.model.response.JobResponse;
import com.sinai.workflow.model.response.JobSummaryResponse;
import com.sinai.workflow.model.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Jobs", description = "Subtitle jobs")
@RequestMapping("/api/v1/jobs")
@ApiResponse(responseCode = "4XX", description = "Client error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "5XX", description = "Server error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
public interface JobApi {

    @Operation(operationId = "createJob", summary = "Upload an .mp4 video and create a PENDING subtitle job",
            requestBody = @RequestBody(required = true, content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = CreateJobRequest.class))))
    @ApiResponse(responseCode = "201", description = "Job created")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<JobResponse> createJob(
            @Parameter(hidden = true) @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @RequestParam("sourceLanguage") String sourceLanguage,
            @Parameter(hidden = true) @RequestParam("targetLanguages") List<String> targetLanguages);

    @Operation(operationId = "getJob", summary = "Get a job with its translation tasks and download URLs")
    @ApiResponse(responseCode = "200", description = "Job found")
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    JobResponse getJob(@PathVariable("id") UUID id);

    @Operation(operationId = "listJobs", summary = "List jobs, newest first")
    @ApiResponse(responseCode = "200", description = "Page of jobs")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    PageResponse<JobSummaryResponse> listJobs(
            @Parameter(description = "Zero-based page index") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(name = "size", defaultValue = "20") int size);
}
