package com.sinai.identity.service;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.config.RefreshSessionProperties;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.model.response.TokenResponse;
import com.sinai.identity.repository.RefreshSessionRepository;
import com.sinai.identity.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    static final int REFRESH_TOKEN_BYTES = 32;
    static final int MAX_REFRESH_TOKEN_LENGTH = 128;

    private final RefreshSessionRepository sessionRepository;
    private final UserAccountRepository userRepository;
    private final TokenService tokenService;
    private final RefreshSessionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public IssuedSession start(UserAccount user) {
        var now = clock.instant();
        sessionRepository.deleteExpired(now);
        var refreshToken = newRefreshToken();
        sessionRepository.insert(UUID.randomUUID(), user.getId(), hash(refreshToken), now.plus(properties.ttl()));
        return new IssuedSession(accessTokenFor(user), refreshToken, properties.ttl());
    }

    @Transactional
    public IssuedSession refresh(String refreshToken) {
        if (!isWellFormed(refreshToken)) {
            throw new SinAiException(ErrorCode.UNAUTHORIZED);
        }
        var now = clock.instant();
        var session = sessionRepository.lockActiveByTokenHash(hash(refreshToken), now)
                .orElseThrow(() -> new SinAiException(ErrorCode.UNAUTHORIZED));
        var user = userRepository.findById(session.userId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new SinAiException(ErrorCode.UNAUTHORIZED));
        var rotatedToken = newRefreshToken();
        sessionRepository.rotate(session.id(), hash(rotatedToken), now);
        return new IssuedSession(accessTokenFor(user), rotatedToken, Duration.between(now, session.expiresAt()));
    }

    @Transactional
    public void end(String refreshToken) {
        if (isWellFormed(refreshToken)) {
            sessionRepository.deleteByTokenHash(hash(refreshToken));
        }
    }

    @Transactional
    public void endAll(UUID userId) {
        sessionRepository.deleteByUserId(userId);
    }

    private TokenResponse accessTokenFor(UserAccount user) {
        return new TokenResponse(tokenService.issueAccessToken(user), AuthService.TOKEN_TYPE,
                tokenService.accessTokenTtlSeconds(), AuthService.toResponse(user));
    }

    private String newRefreshToken() {
        var bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isWellFormed(String refreshToken) {
        return StringUtils.isNotBlank(refreshToken) && refreshToken.length() <= MAX_REFRESH_TOKEN_LENGTH;
    }

    static String hash(String refreshToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
