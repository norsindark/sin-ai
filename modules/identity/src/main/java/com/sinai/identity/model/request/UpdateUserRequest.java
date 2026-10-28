package com.sinai.identity.model.request;

import com.sinai.identity.api.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateUserRequest(
        @Schema(description = "New role; unchanged when absent") Role role,
        @Schema(description = "Enable or disable login; unchanged when absent") Boolean enabled) {
}
