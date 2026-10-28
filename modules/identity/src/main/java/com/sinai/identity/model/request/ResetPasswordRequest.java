package com.sinai.identity.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResetPasswordRequest(
        @Schema(description = "8 to 72 UTF-8 bytes", minLength = 8, maxLength = 72,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword) {
}
