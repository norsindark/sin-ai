package com.sinai.workflow.controller;

import com.sinai.workflow.api.JobApi;
import com.sinai.workflow.model.request.CreateJobRequest;
import com.sinai.workflow.model.response.JobResponse;
import com.sinai.workflow.model.response.JobSummaryResponse;
import com.sinai.workflow.model.response.PageResponse;
import com.sinai.workflow.service.JobService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class JobController implements JobApi {

    private final JobService jobService;

    @Override
    public ResponseEntity<JobResponse> createJob(MultipartFile file, String sourceLanguage,
            List<String> targetLanguages) {
        var job = jobService.createJob(new CreateJobRequest(file, sourceLanguage, targetLanguages));
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(job.id())
                .toUri();
        return ResponseEntity.created(location).body(job);
    }

    @Override
    public JobResponse getJob(UUID id) {
        return jobService.getJob(id);
    }

    @Override
    public PageResponse<JobSummaryResponse> listJobs(int page, int size) {
        return jobService.listJobs(page, size);
    }
}
