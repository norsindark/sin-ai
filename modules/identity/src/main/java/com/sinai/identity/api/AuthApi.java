package com.sinai.identity.api;

import com.sinai.common.dto.ApiErrorResponse;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.model.request.ChangeEmailRequest;
import com.sinai.identity.model.request.ChangePasswordRequest;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.model.response.TokenResponse;
import com.sinai.identity.model.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
            description = "Rejected with 409 USERNAME_ALREADY_EXISTS or EMAIL_ALREADY_EXISTS when either is taken, "
                    + "and with 429 TOO_MANY_ATTEMPTS (Retry-After in seconds) after too many registrations "
                    + "from one client")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Account created")
    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request);

    @Operation(operationId = "login", summary = "Exchange a username or email and password for a bearer access token",
            description = "Also sets the HttpOnly refresh cookie. Unknown account, wrong password and disabled "
                    + "accounts all return 401 INVALID_CREDENTIALS. Too many failures for one login or one client "
                    + "return 429 TOO_MANY_ATTEMPTS (Retry-After in seconds)")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Logged in")
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request);

    @Operation(operationId = "refresh", summary = "Exchange the refresh cookie for a new access token",
            description = "Rotates the refresh cookie on every call; 401 UNAUTHORIZED when it is missing, expired, "
                    + "already rotated or its user is disabled. Send no Authorization header")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "New access token")
    @PostMapping(path = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = SecurityConfig.REFRESH_COOKIE, required = false)
            String refreshToken);

    @Operation(operationId = "logout", summary = "Revoke the bearer token and the refresh cookie of this request",
            description = "The token is rejected with 401 from then on, even before it expires")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping(path = "/logout")
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = SecurityConfig.REFRESH_COOKIE, required = false)
            String refreshToken);

    @Operation(operationId = "me", summary = "Get the authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user")
    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    UserResponse me();

    @Operation(operationId = "changePassword", summary = "Change the password of the authenticated user",
            description = "400 INVALID_CURRENT_PASSWORD when the current password is wrong (throttled like login). "
                    + "Signs out every other session and returns a fresh token and refresh cookie")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @PutMapping(path = "/me/password", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> changePassword(@RequestBody ChangePasswordRequest request);

    @Operation(operationId = "changeEmail", summary = "Change the email of the authenticated user",
            description = "Requires the current password; 409 EMAIL_ALREADY_EXISTS when taken. "
                    + "Signs out every other session and returns a fresh token and refresh cookie")
    @ApiResponse(responseCode = "200", description = "Email changed")
    @PutMapping(path = "/me/email", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> changeEmail(@RequestBody ChangeEmailRequest request);
}
