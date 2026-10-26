package com.sinai.workflow.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record RenameJobRequest(
        @Schema(description = "New display name of the video", maxLength = 255,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title) {
}
