package com.sinai.identity.service;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.api.Role;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.model.response.TokenResponse;
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
    private final TokenService tokenService;
    private final String unknownUserHash;

    public AuthService(UserAccountRepository userRepository, TokenStateRepository tokenStateRepository,
            PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenStateRepository = tokenStateRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request) {
        if (Objects.isNull(request)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "request body must not be empty");
        }
        var username = CredentialPolicy.validateUsername(request.username());
        var email = CredentialPolicy.validateEmail(request.email());
        var password = CredentialPolicy.validatePassword(request.password());
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
            var errorCode = StringUtils.contains(e.getMessage(), "uq_users_email")
                    ? ErrorCode.EMAIL_ALREADY_EXISTS
                    : ErrorCode.USERNAME_ALREADY_EXISTS;
            throw new SinAiException(errorCode, errorCode.getDefaultMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        var login = Objects.isNull(request) ? null : StringUtils.trim(request.login());
        var password = Objects.isNull(request) ? null : request.password();
        if (StringUtils.isAnyBlank(login, password)) {
            throw new SinAiException(ErrorCode.INVALID_CREDENTIALS);
        }
        var user = (CredentialPolicy.looksLikeEmail(login)
                ? userRepository.findByEmail(CredentialPolicy.normalizeEmail(login))
                : userRepository.findByUsername(CredentialPolicy.normalizeUsername(login)))
                .orElse(null);
        var hash = Objects.isNull(user) ? unknownUserHash : user.getPasswordHash();
        var passwordMatches = passwordEncoder.matches(password, hash);
        if (Objects.isNull(user) || !passwordMatches || !user.isEnabled()) {
            throw new SinAiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return new TokenResponse(tokenService.issueAccessToken(user), TOKEN_TYPE,
                tokenService.accessTokenTtlSeconds(), toResponse(user));
    }

    @Transactional
    public void logout(Jwt token) {
        var now = Instant.now();
        tokenStateRepository.deleteExpired(now);
        tokenStateRepository.revoke(UUID.fromString(token.getId()),
                Objects.requireNonNullElse(token.getExpiresAt(), now));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser currentUser) {
        return userRepository.findById(currentUser.id())
                .map(AuthService::toResponse)
                .orElseThrow(() -> new SinAiException(ErrorCode.USER_NOT_FOUND));
    }

    static UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isEnabled(),
                user.getCreatedAt());
    }
}
