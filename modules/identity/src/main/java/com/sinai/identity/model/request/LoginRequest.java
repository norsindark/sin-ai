package com.sinai.identity.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(description = "Username or email", requiredMode = Schema.RequiredMode.REQUIRED) String login,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password) {
}
