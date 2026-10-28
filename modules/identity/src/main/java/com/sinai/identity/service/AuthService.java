package com.sinai.identity.service;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.api.Role;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.model.request.ChangeEmailRequest;
import com.sinai.identity.model.request.ChangePasswordRequest;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.repository.TokenStateRepository;
import com.sinai.identity.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String TOKEN_TYPE = "Bearer";

    private final UserAccountRepository userRepository;
    private final TokenStateRepository tokenStateRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuthThrottle authThrottle;
    private final String unknownUserHash;

    public AuthService(UserAccountRepository userRepository, TokenStateRepository tokenStateRepository,
            PasswordEncoder passwordEncoder, SessionService sessionService, AuthThrottle authThrottle) {
        this.userRepository = userRepository;
        this.tokenStateRepository = tokenStateRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.authThrottle = authThrottle;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request, String clientAddress) {
        if (Objects.isNull(request)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "request body must not be empty");
        }
        var username = CredentialPolicy.validateUsername(request.username());
        var email = CredentialPolicy.validateEmail(request.email());
        var password = CredentialPolicy.validatePassword(request.password());
        authThrottle.acquireRegistration(clientAddress);
        return toResponse(createUser(username, email, password, Role.USER));
    }

    UserAccount createUser(String username, String email, String password, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new SinAiException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmail(email)) {
            throw new SinAiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        try {
            return userRepository.saveAndFlush(UserAccount.create(username, email, passwordEncoder.encode(password),
                    role));
        } catch (DataIntegrityViolationException e) {
            throw duplicateAccount(e);
        }
    }

    @Transactional
    public IssuedSession login(LoginRequest request, String clientAddress) {
        var login = Objects.isNull(request) ? null : StringUtils.trim(request.login());
        var password = Objects.isNull(request) ? null : request.password();
        if (StringUtils.isAnyBlank(login, password)) {
            throw new SinAiException(ErrorCode.INVALID_CREDENTIALS);
        }
        authThrottle.checkLogin(login, clientAddress);
        UserAccount user;
        try {
            user = authenticate(login, password);
        } catch (SinAiException e) {
            authThrottle.recordLoginFailure(login, clientAddress);
            throw e;
        }
        authThrottle.recordLoginSuccess(login, clientAddress);
        return sessionService.start(user);
    }

    private UserAccount authenticate(String login, String password) {
        var user = (CredentialPolicy.looksLikeEmail(login)
                ? userRepository.findByEmail(CredentialPolicy.normalizeEmail(login))
                : userRepository.findByUsername(CredentialPolicy.normalizeUsername(login)))
                .orElse(null);
        var hash = Objects.isNull(user) ? unknownUserHash : user.getPasswordHash();
        var passwordMatches = passwordEncoder.matches(password, hash);
        if (Objects.isNull(user) || !passwordMatches || !user.isEnabled()) {
            throw new SinAiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return user;
    }

    public IssuedSession refresh(String refreshToken) {
        return sessionService.refresh(refreshToken);
    }

    @Transactional
    public void logout(Jwt token, String refreshToken) {
        var now = Instant.now();
        tokenStateRepository.deleteExpired(now);
        tokenStateRepository.revoke(UUID.fromString(token.getId()),
                Objects.requireNonNullElse(token.getExpiresAt(), now));
        sessionService.end(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser currentUser) {
        return userRepository.findById(currentUser.id())
                .map(AuthService::toResponse)
                .orElseThrow(() -> new SinAiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public IssuedSession changePassword(CurrentUser currentUser, ChangePasswordRequest request,
            String clientAddress) {
        if (Objects.isNull(request)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "request body must not be empty");
        }
        var newPassword = CredentialPolicy.validatePassword(request.newPassword());
        var user = lockVerified(currentUser, request.currentPassword(), clientAddress);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.flush();
        sessionService.endAll(user.getId());
        return sessionService.start(user);
    }

    @Transactional
    public IssuedSession changeEmail(CurrentUser currentUser, ChangeEmailRequest request, String clientAddress) {
        if (Objects.isNull(request)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "request body must not be empty");
        }
        var email = CredentialPolicy.validateEmail(request.email());
        var user = lockVerified(currentUser, request.currentPassword(), clientAddress);
        if (Objects.equals(user.getEmail(), email)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "email is unchanged");
        }
        if (userRepository.existsByEmail(email)) {
            throw new SinAiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        user.changeEmail(email);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicateAccount(e);
        }
        sessionService.endAll(user.getId());
        return sessionService.start(user);
    }

    private UserAccount lockVerified(CurrentUser currentUser, String currentPassword, String clientAddress) {
        authThrottle.checkLogin(currentUser.username(), clientAddress);
        var user = userRepository.findForUpdateById(currentUser.id())
                .orElseThrow(() -> new SinAiException(ErrorCode.USER_NOT_FOUND));
        if (StringUtils.isBlank(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            authThrottle.recordLoginFailure(currentUser.username(), clientAddress);
            throw new SinAiException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        authThrottle.recordLoginSuccess(currentUser.username(), clientAddress);
        return user;
    }

    static SinAiException duplicateAccount(DataIntegrityViolationException e) {
        var errorCode = StringUtils.contains(e.getMessage(), "uq_users_email")
                ? ErrorCode.EMAIL_ALREADY_EXISTS
                : ErrorCode.USERNAME_ALREADY_EXISTS;
        return new SinAiException(errorCode, errorCode.getDefaultMessage(), e);
    }

    static UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isEnabled(),
                user.getCreatedAt());
    }
}
