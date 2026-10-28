package com.sinai.identity.api;

import com.sinai.common.dto.ApiErrorResponse;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.model.response.TokenResponse;
import com.sinai.identity.model.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Auth", description = "Registration, login and the current user")
@RequestMapping("/api/v1/auth")
@ApiResponse(responseCode = "4XX", description = "Client error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "5XX", description = "Server error",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
public interface AuthApi {

    @Operation(operationId = "register", summary = "Create a USER account",
            description = "Rejected with 409 USERNAME_ALREADY_EXISTS or EMAIL_ALREADY_EXISTS when either is taken")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Account created")
    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request);

    @Operation(operationId = "login", summary = "Exchange a username or email and password for a bearer access token",
            description = "Unknown account, wrong password and disabled accounts all return 401 INVALID_CREDENTIALS")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Logged in")
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    TokenResponse login(@RequestBody LoginRequest request);

    @Operation(operationId = "logout", summary = "Revoke the bearer token of this request",
            description = "The token is rejected with 401 from then on, even before it expires")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping(path = "/logout")
    ResponseEntity<Void> logout();

    @Operation(operationId = "me", summary = "Get the authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user")
    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    UserResponse me();
}
