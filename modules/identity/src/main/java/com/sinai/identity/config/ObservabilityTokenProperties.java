package com.sinai.identity.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.security.observability")
public record ObservabilityTokenProperties(String token) {

    static final int MIN_TOKEN_LENGTH = 32;

    public ObservabilityTokenProperties {
        token = StringUtils.trimToNull(token);
        if (StringUtils.isNotEmpty(token) && token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalArgumentException("sinai.security.observability.token must be at least "
                    + MIN_TOKEN_LENGTH + " characters");
        }
    }

    public boolean enabled() {
        return StringUtils.isNotEmpty(token);
    }
}
