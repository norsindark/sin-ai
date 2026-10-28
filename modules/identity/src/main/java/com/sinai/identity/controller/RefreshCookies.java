package com.sinai.identity.controller;

import com.sinai.identity.config.RefreshSessionProperties;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.model.response.TokenResponse;
import com.sinai.identity.service.IssuedSession;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshCookies {

    static final String COOKIE_PATH = "/api/v1/auth";

    private final RefreshSessionProperties properties;

    public ResponseEntity<TokenResponse> withSession(HttpStatus status, IssuedSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), session.refreshTokenTtl()).toString())
                .body(session.token());
    }

    public String cleared() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(SecurityConfig.REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
