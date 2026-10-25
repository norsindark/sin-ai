package com.sinai.workflow.model.response;

import com.sinai.common.constant.JobStatus;
import com.sinai.workflow.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.Schema;

public record TranslationTaskResponse(
        @Schema(ref = OpenApiConfig.LANGUAGE_CODE_REF, requiredMode = Schema.RequiredMode.REQUIRED)
        String targetLanguage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) JobStatus status,
        @Schema(nullable = true, description = "Presigned download URL of the translated .srt, set once completed")
        String subtitleUrl,
        @Schema(nullable = true) String errorMessage) {
}
