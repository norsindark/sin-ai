package com.sinai.identity.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Bearer") String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Lifetime in seconds") long expiresIn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserResponse user) {
}
