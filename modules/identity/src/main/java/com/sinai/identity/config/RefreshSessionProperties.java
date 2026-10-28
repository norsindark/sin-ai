package com.sinai.identity.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.security.refresh")
public record RefreshSessionProperties(Duration ttl, Boolean cookieSecure) {

    public RefreshSessionProperties {
        ttl = Objects.requireNonNullElse(ttl, Duration.ofDays(14));
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("sinai.security.refresh.ttl must be positive");
        }
        cookieSecure = Objects.requireNonNullElse(cookieSecure, Boolean.TRUE);
    }
}
