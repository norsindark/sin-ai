package com.sinai.identity.api;

import com.sinai.common.dto.ApiErrorResponse;
import com.sinai.identity.model.request.ResetPasswordRequest;
import com.sinai.identity.model.request.UpdateUserRequest;
import com.sinai.identity.model.response.UserPageResponse;
import com.sinai.identity.model.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Users", description = "User management (ADMIN only)")
@RequestMapping("/api/v1/users")
@ApiResponse(responseCode = "4XX", description = "Client error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "5XX", description = "Server error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
public interface UserApi {

    @Operation(operationId = "listUsers", summary = "List users, newest first")
    @ApiResponse(responseCode = "200", description = "Page of users")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    UserPageResponse listUsers(
            @Parameter(description = "Zero-based page index") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(name = "size", defaultValue = "20") int size);

    @Operation(operationId = "updateUser", summary = "Change the role or enabled flag of a user",
            description = "Rejected with 409 LAST_ADMIN when it would leave no enabled admin. "
                    + "Existing tokens of the user stop working at once")
    @ApiResponse(responseCode = "200", description = "User updated")
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    UserResponse updateUser(@PathVariable("id") UUID id, @RequestBody UpdateUserRequest request);

    @Operation(operationId = "resetUserPassword", summary = "Set a new password for a user",
            description = "Signs the user out of every session")
    @ApiResponse(responseCode = "200", description = "Password reset")
    @PutMapping(path = "/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    UserResponse resetPassword(@PathVariable("id") UUID id, @RequestBody ResetPasswordRequest request);

    @Operation(operationId = "deleteUser", summary = "Delete a user and transfer their videos to the calling admin",
            description = "409 CANNOT_DELETE_SELF for your own account, 409 LAST_ADMIN for the last enabled admin. "
                    + "Every job of the user, including running ones, becomes owned by the calling admin")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @DeleteMapping(path = "/{id}")
    ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id);
}
