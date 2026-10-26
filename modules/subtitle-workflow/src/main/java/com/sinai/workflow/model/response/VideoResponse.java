package com.sinai.workflow.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record VideoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String originalFilename,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Display name chosen by the user")
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sizeBytes) {
}
