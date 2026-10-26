package com.sinai.workflow.model.response;

import com.sinai.common.constant.JobStatus;
import com.sinai.workflow.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) JobStatus status,
        @Schema(ref = OpenApiConfig.LANGUAGE_CODE_REF, requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceLanguage,
        @ArraySchema(schema = @Schema(ref = OpenApiConfig.LANGUAGE_CODE_REF),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<String> targetLanguages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String originalFilename,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Display name chosen by the user")
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {
}
