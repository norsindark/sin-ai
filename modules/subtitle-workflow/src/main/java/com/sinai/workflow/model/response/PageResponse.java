package com.sinai.workflow.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PageResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {
}
