package com.sinai.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.common.exception.TooManyAttemptsException;
import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.api.Role;
import com.sinai.identity.config.JwtProperties;
import com.sinai.identity.config.RefreshSessionProperties;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.config.ThrottleProperties;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.model.request.ChangeEmailRequest;
import com.sinai.identity.model.request.ChangePasswordRequest;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.repository.RefreshSessionRepository;
import com.sinai.identity.repository.RefreshSessionRepository.RefreshSession;
import com.sinai.identity.repository.TokenStateRepository;
import com.sinai.identity.repository.TokenStateRepository.TokenSubjectState;
import com.sinai.identity.repository.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class AuthServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String CLIENT = "203.0.113.7";

    private UserAccountRepository userRepository;
    private TokenStateRepository tokenStateRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService service;
    private JwtDecoder jwtDecoder;
    private AuthThrottle authThrottle;
    private RefreshSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserAccountRepository.class);
        tokenStateRepository = mock(TokenStateRepository.class);
        var securityConfig = new SecurityConfig();
        passwordEncoder = securityConfig.passwordEncoder();
        var jwtProperties = new JwtProperties(SECRET, null, Duration.ofMinutes(30));
        var secretKey = securityConfig.jwtSecretKey(jwtProperties);
        var tokenService = new TokenService(securityConfig.jwtEncoder(secretKey), jwtProperties);
        jwtDecoder = securityConfig.jwtDecoder(secretKey, jwtProperties, tokenStateRepository);
        authThrottle = new AuthThrottle(new ThrottleProperties(3, 100, Duration.ofMinutes(15), 100,
                Duration.ofHours(1)));
        sessionRepository = mock(RefreshSessionRepository.class);
        var sessionService = new SessionService(sessionRepository, userRepository, tokenService,
                new RefreshSessionProperties(null, null));
        service = new AuthService(userRepository, tokenStateRepository, passwordEncoder, sessionService,
                authThrottle);
        when(userRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registerNormalizesUsernameAndEmailHashesPasswordAndAlwaysCreatesUserRole() {
        var response = service.register(new RegisterRequest("  Alice.Nguyen ", " Alice@Example.COM ",
                "correct horse"), CLIENT);

        assertThat(response.username()).isEqualTo("alice.nguyen");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.enabled()).isTrue();
        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("correct horse", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsTakenUsernameOrEmailFromPreCheckAndFromUniqueIndex() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "taken", "free@example.com", "password1"), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS));
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "free", "taken@example.com", "password1"), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

        when(userRepository.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key violates uq_users_email"));
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "racer", "race@example.com", "password1"), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''                                | alice@example.com | password1",
            "ab                                | alice@example.com | password1",
            "has space                         | alice@example.com | password1",
            "alice@example.com                 | alice@example.com | password1",
            "abcdefghijklmnopqrstuvwxyz0123456 | alice@example.com | password1",
            "alice                             | ''                | password1",
            "alice                             | not-an-email      | password1",
            "alice                             | a@b               | password1",
            "alice                             | alice@example.com | short",
            "alice                             | alice@example.com | ''"})
    void registerRejectsInvalidCredentials(String username, String email, String password) {
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                username, email, password), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() {
        var password = "ă".repeat(37);

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "alice", "alice@example.com", password), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void loginByUsernameOrEmailIssuesSignedTokenWithIdSubjectAndRole() {
        var user = activeUser(Role.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenStateRepository.findSubjectState(any(), any()))
                .thenReturn(Optional.of(new TokenSubjectState(Role.ADMIN, true, 0, false)));

        var byUsername = service.login(new LoginRequest(" ALICE", "password1"), CLIENT).token();
        var byEmail = service.login(new LoginRequest("Alice@Example.com ", "password1"), CLIENT).token();

        assertThat(byUsername.tokenType()).isEqualTo("Bearer");
        assertThat(byUsername.expiresIn()).isEqualTo(1800);
        assertThat(byEmail.user().id()).isEqualTo(user.getId());
        var decoded = jwtDecoder.decode(byUsername.accessToken());
        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(UUID.fromString(decoded.getId())).isNotNull();
        assertThat(decoded.getClaimAsString(SecurityConfig.USERNAME_CLAIM)).isEqualTo("alice");
        assertThat(decoded.getClaimAsString(SecurityConfig.EMAIL_CLAIM)).isEqualTo("alice@example.com");
        assertThat(decoded.getClaimAsStringList(SecurityConfig.ROLES_CLAIM)).containsExactly("ADMIN");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("sin-ai");
        assertThat(decoded.getId()).isNotEqualTo(jwtDecoder.decode(byEmail.accessToken()).getId());
    }

    @Test
    void decoderRejectsRevokedTokensDisabledUsersChangedRolesAndDeletedUsers() {
        var user = activeUser(Role.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        var accessToken = service.login(new LoginRequest("alice", "password1"), CLIENT).token().accessToken();

        for (var state : new TokenSubjectState[] {
                new TokenSubjectState(Role.ADMIN, true, 0, true),
                new TokenSubjectState(Role.ADMIN, false, 0, false),
                new TokenSubjectState(Role.USER, true, 0, false),
                new TokenSubjectState(Role.ADMIN, true, 1, false)}) {
            when(tokenStateRepository.findSubjectState(any(), any())).thenReturn(Optional.of(state));
            assertThatThrownBy(() -> jwtDecoder.decode(accessToken)).isInstanceOf(JwtValidationException.class);
        }
        when(tokenStateRepository.findSubjectState(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> jwtDecoder.decode(accessToken)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void logoutRevokesTokenUntilItExpiresAndPurgesExpiredEntries() {
        var user = activeUser(Role.USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tokenStateRepository.findSubjectState(any(), any()))
                .thenReturn(Optional.of(new TokenSubjectState(Role.USER, true, 0, false)));
        var jwt = jwtDecoder.decode(
                service.login(new LoginRequest("alice", "password1"), CLIENT).token().accessToken());

        service.logout(jwt, null);

        verify(tokenStateRepository).deleteExpired(any(Instant.class));
        verify(tokenStateRepository).revoke(UUID.fromString(jwt.getId()), jwt.getExpiresAt());
    }

    @Test
    void loginFailsWithSameErrorForUnknownAccountWrongPasswordAndDisabledUser() {
        var user = activeUser(Role.USER);
        var disabled = UserAccount.create("bob", "bob@example.com", passwordEncoder.encode("password1"), Role.USER);
        disabled.changeEnabled(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(disabled));
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        for (var request : new LoginRequest[] {
                new LoginRequest("alice", "wrong-password"),
                new LoginRequest("bob", "password1"),
                new LoginRequest("nobody", "password1"),
                new LoginRequest("nobody@example.com", "password1"),
                new LoginRequest("", ""),
                null}) {
            assertThatThrownBy(() -> service.login(request, CLIENT))
                    .isInstanceOfSatisfying(SinAiException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                        assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
                    });
        }
    }

    @Test
    void loginStoresOnlyTheHashOfTheRefreshTokenAndRefreshRotatesIt() {
        var user = activeUser(Role.USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var session = service.login(new LoginRequest("alice", "password1"), CLIENT);

        var storedHash = ArgumentCaptor.forClass(String.class);
        verify(sessionRepository).insert(any(UUID.class), eq(user.getId()), storedHash.capture(), any(Instant.class));
        assertThat(storedHash.getValue()).isEqualTo(SessionService.hash(session.refreshToken()))
                .isNotEqualTo(session.refreshToken());
        assertThat(session.refreshTokenTtl()).isEqualTo(Duration.ofDays(14));

        var sessionId = UUID.randomUUID();
        when(sessionRepository.lockActiveByTokenHash(eq(storedHash.getValue()), any(Instant.class)))
                .thenReturn(Optional.of(new RefreshSession(sessionId, user.getId(),
                        Instant.now().plus(Duration.ofDays(1)))));
        var refreshed = service.refresh(session.refreshToken());

        assertThat(refreshed.refreshToken()).isNotEqualTo(session.refreshToken());
        assertThat(refreshed.token().user().id()).isEqualTo(user.getId());
        verify(sessionRepository).rotate(eq(sessionId), eq(SessionService.hash(refreshed.refreshToken())),
                any(Instant.class));
        assertThatThrownBy(() -> service.refresh("unknown-token"))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(SinAiException.class);
    }

    @Test
    void refreshRejectsDisabledUsers() {
        var user = activeUser(Role.USER);
        user.changeEnabled(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(sessionRepository.lockActiveByTokenHash(any(), any()))
                .thenReturn(Optional.of(new RefreshSession(UUID.randomUUID(), user.getId(), Instant.now())));

        assertThatThrownBy(() -> service.refresh("some-token"))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(sessionRepository, never()).rotate(any(), any(), any());
    }

    @Test
    void changePasswordVerifiesTheCurrentPasswordBumpsTheTokenVersionAndEndsOtherSessions() {
        var user = activeUser(Role.USER);
        var currentUser = new CurrentUser(user.getId(), "alice", Role.USER);
        when(userRepository.findForUpdateById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(currentUser,
                new ChangePasswordRequest("wrong-password", "new-password"), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD));
        assertThatThrownBy(() -> service.changePassword(currentUser,
                new ChangePasswordRequest("password1", "short"), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));

        var session = service.changePassword(currentUser, new ChangePasswordRequest("password1", "new-password"),
                CLIENT);

        assertThat(passwordEncoder.matches("new-password", user.getPasswordHash())).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(sessionRepository).deleteByUserId(user.getId());
        when(tokenStateRepository.findSubjectState(any(), any()))
                .thenReturn(Optional.of(new TokenSubjectState(Role.USER, true, 1, false)));
        assertThat(jwtDecoder.decode(session.token().accessToken())
                .<Long>getClaim(SecurityConfig.TOKEN_VERSION_CLAIM)).isEqualTo(1L);
    }

    @Test
    void currentPasswordFailuresAreThrottledLikeLogins() {
        var user = activeUser(Role.USER);
        var currentUser = new CurrentUser(user.getId(), "alice", Role.USER);
        when(userRepository.findForUpdateById(user.getId())).thenReturn(Optional.of(user));
        for (var attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.changeEmail(currentUser,
                    new ChangeEmailRequest("new@example.com", "wrong-password"), CLIENT))
                    .isInstanceOf(SinAiException.class);
        }

        assertThatThrownBy(() -> service.changeEmail(currentUser,
                new ChangeEmailRequest("new@example.com", "password1"), CLIENT))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void changeEmailNormalizesRejectsTakenOrUnchangedEmailAndIssuesTokenWithTheNewEmail() {
        var user = activeUser(Role.USER);
        var currentUser = new CurrentUser(user.getId(), "alice", Role.USER);
        when(userRepository.findForUpdateById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.changeEmail(currentUser,
                new ChangeEmailRequest("Taken@Example.com", "password1"), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        assertThatThrownBy(() -> service.changeEmail(currentUser,
                new ChangeEmailRequest("alice@example.com", "password1"), CLIENT))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));

        var session = service.changeEmail(currentUser, new ChangeEmailRequest(" New@Example.com ", "password1"),
                CLIENT);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(session.token().user().email()).isEqualTo("new@example.com");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(sessionRepository).deleteByUserId(user.getId());
    }

    @Test
    void jwtPropertiesRejectShortSecretAndNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties("too-short", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET, null, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new JwtProperties(SECRET, null, null).accessTokenTtl()).isEqualTo(Duration.ofHours(1));
    }

    private UserAccount activeUser(Role role) {
        return UserAccount.create("alice", "alice@example.com", passwordEncoder.encode("password1"), role);
    }
}
