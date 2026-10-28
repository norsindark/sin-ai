package com.sinai.identity.service;

import com.sinai.common.exception.TooManyAttemptsException;
import com.sinai.identity.config.ThrottleProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthThrottle {

    private static final String UNKNOWN_CLIENT = "unknown";

    private final AttemptLimiter accountLoginFailures;
    private final AttemptLimiter clientLoginFailures;
    private final AttemptLimiter clientRegistrations;

    @Autowired
    public AuthThrottle(ThrottleProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthThrottle(ThrottleProperties properties, Clock clock) {
        accountLoginFailures = new AttemptLimiter(properties.maxLoginFailuresPerAccount(),
                properties.loginWindow(), clock);
        clientLoginFailures = new AttemptLimiter(properties.maxLoginFailuresPerClient(),
                properties.loginWindow(), clock);
        clientRegistrations = new AttemptLimiter(properties.maxRegistrationsPerClient(),
                properties.registrationWindow(), clock);
    }

    public void checkLogin(String login, String clientAddress) {
        var client = clientKey(clientAddress);
        Stream.of(accountLoginFailures.blockedFor(accountKey(login, client)), clientLoginFailures.blockedFor(client))
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder())
                .ifPresent(AuthThrottle::reject);
    }

    public void recordLoginFailure(String login, String clientAddress) {
        var client = clientKey(clientAddress);
        accountLoginFailures.record(accountKey(login, client));
        clientLoginFailures.record(client);
    }

    public void recordLoginSuccess(String login, String clientAddress) {
        accountLoginFailures.reset(accountKey(login, clientKey(clientAddress)));
    }

    public void acquireRegistration(String clientAddress) {
        var client = clientKey(clientAddress);
        clientRegistrations.blockedFor(client).ifPresent(AuthThrottle::reject);
        clientRegistrations.record(client);
    }

    private static String clientKey(String clientAddress) {
        return StringUtils.defaultIfBlank(StringUtils.trim(clientAddress), UNKNOWN_CLIENT);
    }

    private static String accountKey(String login, String client) {
        return client + "|" + StringUtils.lowerCase(StringUtils.trimToEmpty(login), Locale.ROOT);
    }

    private static void reject(Duration retryAfter) {
        throw new TooManyAttemptsException(retryAfter);
    }
}
