package com.sinai.workflow.model.response;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.exception.ErrorCode;
import com.sinai.workflow.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) JobStatus status,
        @Schema(ref = OpenApiConfig.LANGUAGE_CODE_REF, requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceLanguage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VideoResponse video,
        @Schema(nullable = true, description = "Presigned download URL of the source-language .srt")
        String sourceSubtitleUrl,
        @Schema(nullable = true, description = "Presigned download URL of the subtitled video")
        String outputVideoUrl,
        @Schema(nullable = true) ErrorCode errorCode,
        @Schema(nullable = true) String errorMessage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(nullable = true) Instant startedAt,
        @Schema(nullable = true) Instant completedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TranslationTaskResponse> translations) {
}
