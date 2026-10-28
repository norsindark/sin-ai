package com.sinai.identity.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @Schema(description = "3-32 characters of a-z, 0-9, '.', '_' or '-'; stored lowercase", minLength = 3,
                maxLength = 32, requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(description = "Email address, stored lowercase", maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "8 to 72 UTF-8 bytes", minLength = 8, maxLength = 72,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password) {
}
