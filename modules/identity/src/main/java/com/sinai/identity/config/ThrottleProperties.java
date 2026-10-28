package com.sinai.identity.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.security.throttle")
public record ThrottleProperties(Integer maxLoginFailuresPerAccount, Integer maxLoginFailuresPerClient,
        Duration loginWindow, Integer maxRegistrationsPerClient, Duration registrationWindow) {

    public ThrottleProperties {
        maxLoginFailuresPerAccount = requirePositive(Objects.requireNonNullElse(maxLoginFailuresPerAccount, 5),
                "max-login-failures-per-account");
        maxLoginFailuresPerClient = requirePositive(Objects.requireNonNullElse(maxLoginFailuresPerClient, 20),
                "max-login-failures-per-client");
        loginWindow = requirePositive(Objects.requireNonNullElse(loginWindow, Duration.ofMinutes(15)),
                "login-window");
        maxRegistrationsPerClient = requirePositive(Objects.requireNonNullElse(maxRegistrationsPerClient, 10),
                "max-registrations-per-client");
        registrationWindow = requirePositive(Objects.requireNonNullElse(registrationWindow, Duration.ofHours(1)),
                "registration-window");
    }

    private static int requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException("sinai.security.throttle." + name + " must be positive");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("sinai.security.throttle." + name + " must be positive");
        }
        return value;
    }
}
