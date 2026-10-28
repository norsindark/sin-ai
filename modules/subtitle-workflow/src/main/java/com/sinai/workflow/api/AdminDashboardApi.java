package com.sinai.workflow.api;

import com.sinai.common.dto.ApiErrorResponse;
import com.sinai.workflow.model.response.AdminDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Admin", description = "Admin overview (ADMIN only)")
@RequestMapping("/api/v1/admin")
@ApiResponse(responseCode = "4XX", description = "Client error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "5XX", description = "Server error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
public interface AdminDashboardApi {

    @Operation(operationId = "getAdminDashboard",
            summary = "User counts, jobs by status, uploaded storage, daily jobs and recent activity")
    @ApiResponse(responseCode = "200", description = "Dashboard")
    @GetMapping(path = "/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    AdminDashboardResponse dashboard();
}
