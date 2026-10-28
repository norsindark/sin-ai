package com.sinai.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.Role;
import com.sinai.identity.config.JwtProperties;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
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

    private UserAccountRepository userRepository;
    private TokenStateRepository tokenStateRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService service;
    private JwtDecoder jwtDecoder;

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
        service = new AuthService(userRepository, tokenStateRepository, passwordEncoder, tokenService);
        when(userRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registerNormalizesUsernameAndEmailHashesPasswordAndAlwaysCreatesUserRole() {
        var response = service.register(new RegisterRequest("  Alice.Nguyen ", " Alice@Example.COM ",
                "correct horse"));

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
        assertThatThrownBy(() -> service.register(new RegisterRequest("taken", "free@example.com", "password1")))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS));
        assertThatThrownBy(() -> service.register(new RegisterRequest("free", "taken@example.com", "password1")))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

        when(userRepository.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key violates uq_users_email"));
        assertThatThrownBy(() -> service.register(new RegisterRequest("racer", "race@example.com", "password1")))
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
        assertThatThrownBy(() -> service.register(new RegisterRequest(username, email, password)))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() {
        var password = "ă".repeat(37);

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice", "alice@example.com", password)))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void loginByUsernameOrEmailIssuesSignedTokenWithIdSubjectAndRole() {
        var user = activeUser(Role.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenStateRepository.findSubjectState(any(), any()))
                .thenReturn(Optional.of(new TokenSubjectState(Role.ADMIN, true, false)));

        var byUsername = service.login(new LoginRequest(" ALICE", "password1"));
        var byEmail = service.login(new LoginRequest("Alice@Example.com ", "password1"));

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
        var accessToken = service.login(new LoginRequest("alice", "password1")).accessToken();

        for (var state : new TokenSubjectState[] {
                new TokenSubjectState(Role.ADMIN, true, true),
                new TokenSubjectState(Role.ADMIN, false, false),
                new TokenSubjectState(Role.USER, true, false)}) {
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
                .thenReturn(Optional.of(new TokenSubjectState(Role.USER, true, false)));
        var jwt = jwtDecoder.decode(service.login(new LoginRequest("alice", "password1")).accessToken());

        service.logout(jwt);

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
            assertThatThrownBy(() -> service.login(request))
                    .isInstanceOfSatisfying(SinAiException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                        assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
                    });
        }
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
