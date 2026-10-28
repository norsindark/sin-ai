package com.sinai.identity.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChangeEmailRequest(
        @Schema(description = "New email address, stored lowercase", maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currentPassword) {
}
