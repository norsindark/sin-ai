package com.sinai.identity.service;

import com.sinai.identity.model.response.TokenResponse;
import java.time.Duration;

public record IssuedSession(TokenResponse token, String refreshToken, Duration refreshTokenTtl) {
}
