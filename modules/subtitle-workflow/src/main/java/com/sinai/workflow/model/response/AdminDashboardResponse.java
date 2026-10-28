package com.sinai.workflow.model.response;

import com.sinai.common.constant.JobStatus;
import com.sinai.identity.api.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminDashboardResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant generatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserStats users,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) JobStats jobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StorageStats storage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Jobs created per UTC day, oldest first, one entry per day including zeros")
        List<DailyCount> dailyJobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RecentJob> recentJobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RecentUser> recentUsers) {

    public record UserStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long enabled,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long activeAdmins,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Registered in the last 7 days")
            long newLast7Days) {
    }

    public record JobStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every status, zeros included")
            List<StatusCount> byStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long createdLast7Days) {
    }

    public record StatusCount(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) JobStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
    }

    public record StorageStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long videoCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Sum of uploaded video sizes; generated files are not counted")
            long uploadedBytes) {
    }

    public record DailyCount(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
    }

    public record RecentJob(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) JobStatus status,
            @Schema(description = "Null for jobs without an owner") String ownerUsername,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
    }

    public record RecentUser(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String username,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
    }
}
