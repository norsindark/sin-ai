package com.sinai.identity.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (StringUtils.isBlank(secret) || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("sinai.security.jwt.secret must be at least " + MIN_SECRET_BYTES
                    + " bytes");
        }
        issuer = StringUtils.defaultIfBlank(issuer, "sin-ai");
        if (Objects.isNull(accessTokenTtl)) {
            accessTokenTtl = Duration.ofHours(1);
        }
        if (accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("sinai.security.jwt.access-token-ttl must be positive");
        }
    }
}
