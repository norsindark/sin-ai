package com.sinai.app.config;

import static com.sinai.app.config.TestJwts.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.common.exception.TooManyAttemptsException;
import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.api.CurrentUserProvider;
import com.sinai.identity.api.Role;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.controller.AuthController;
import com.sinai.identity.controller.RefreshCookies;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.model.response.TokenResponse;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.repository.TokenStateRepository;
import com.sinai.identity.service.AuthService;
import com.sinai.identity.service.IssuedSession;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class, properties = TestJwts.JWT_SECRET_PROPERTY)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, CurrentUserProvider.class, RefreshCookies.class})
class AuthApiWebMvcTest {

    private static final CurrentUser USER = new CurrentUser(UUID.randomUUID(), "alice", Role.USER);
    private static final String REFRESH_TOKEN = "opaque-refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenStateRepository tokenStateRepository;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerIsPublicAndReturnsCreated() throws Exception {
        when(authService.register(any(), anyString())).thenReturn(sampleUser());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));

        var captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture(), eq("127.0.0.1"));
        assertThat(captor.getValue().email()).isEqualTo("alice@example.com");
    }

    @Test
    void loginIsPublicAndSetsHardenedRefreshCookie() throws Exception {
        when(authService.login(any(), anyString())).thenReturn(sampleSession());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(SecurityConfig.REFRESH_COOKIE + "=" + REFRESH_TOKEN),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/v1/auth"),
                        containsString("Max-Age=" + Duration.ofDays(14).toSeconds()))));

        var captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture(), eq("127.0.0.1"));
        assertThat(captor.getValue().login()).isEqualTo("alice");
    }

    @Test
    void loginWithWrongCredentialsReturnsUnauthorizedWithoutCookie() throws Exception {
        when(authService.login(any(), anyString())).thenThrow(new SinAiException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void throttledLoginReturnsTooManyRequestsWithRetryAfter() throws Exception {
        when(authService.login(any(), anyString())).thenThrow(new TooManyAttemptsException(Duration.ofSeconds(90)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        when(authService.register(any(), anyString()))
                .thenThrow(new SinAiException(ErrorCode.USERNAME_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void malformedJsonReturnsInvalidArgument() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        verifyNoInteractions(authService);
    }

    @Test
    void refreshIsPublicAndPassesCookieToService() throws Exception {
        when(authService.refresh(REFRESH_TOKEN)).thenReturn(sampleSession());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(SecurityConfig.REFRESH_COOKIE, REFRESH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(SecurityConfig.REFRESH_COOKIE + "=" + REFRESH_TOKEN)));
    }

    @Test
    void refreshWithUnknownTokenReturnsUnauthorized() throws Exception {
        when(authService.refresh(any())).thenThrow(new SinAiException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointsRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/auth/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"currentPassword\":\"a\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    void meReturnsTheUserFromTheToken() throws Exception {
        when(authService.me(USER)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/v1/auth/me").with(as(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void logoutRevokesTokenAndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(as(USER))
                        .cookie(new Cookie(SecurityConfig.REFRESH_COOKIE, REFRESH_TOKEN)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(SecurityConfig.REFRESH_COOKIE + "="),
                        containsString("Max-Age=0"),
                        containsString("Path=/api/v1/auth"))));

        var captor = ArgumentCaptor.forClass(Jwt.class);
        verify(authService).logout(captor.capture(), eq(REFRESH_TOKEN));
        assertThat(captor.getValue().getSubject()).isEqualTo(USER.id().toString());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordReturnsBadRequest() throws Exception {
        when(authService.changePassword(eq(USER), any(), anyString()))
                .thenThrow(new SinAiException(ErrorCode.INVALID_CURRENT_PASSWORD));

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .with(as(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void changeEmailReturnsFreshSessionCookie() throws Exception {
        when(authService.changeEmail(eq(USER), any(), anyString())).thenReturn(sampleSession());

        mockMvc.perform(put("/api/v1/auth/me/email")
                        .with(as(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"currentPassword\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(SecurityConfig.REFRESH_COOKIE + "=" + REFRESH_TOKEN)));
    }

    private static UserResponse sampleUser() {
        return new UserResponse(USER.id(), USER.username(), "alice@example.com", Role.USER, true,
                Instant.parse("2026-10-25T00:00:00Z"));
    }

    private static IssuedSession sampleSession() {
        var token = new TokenResponse("access-token", "Bearer", 3600, sampleUser());
        return new IssuedSession(token, REFRESH_TOKEN, Duration.ofDays(14));
    }
}
