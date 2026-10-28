package com.sinai.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.TooManyAttemptsException;
import com.sinai.identity.config.ThrottleProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthThrottleTest {

    private static final String CLIENT = "203.0.113.7";
    private static final String OTHER_CLIENT = "198.51.100.9";

    private MutableClock clock;
    private AuthThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-28T00:00:00Z"));
        throttle = new AuthThrottle(new ThrottleProperties(3, 5, Duration.ofMinutes(15), 2, Duration.ofHours(1)),
                clock);
    }

    @Test
    void blocksOneLoginFromOneClientAfterTooManyFailuresUntilTheWindowEnds() {
        for (var attempt = 0; attempt < 3; attempt++) {
            throttle.checkLogin(" Alice ", CLIENT);
            throttle.recordLoginFailure(" Alice ", CLIENT);
        }
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> throttle.checkLogin("alice", CLIENT))
                .isInstanceOfSatisfying(TooManyAttemptsException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS);
                    assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(10));
                });
        assertThatCode(() -> throttle.checkLogin("alice", OTHER_CLIENT)).doesNotThrowAnyException();
        assertThatCode(() -> throttle.checkLogin("bob", CLIENT)).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> throttle.checkLogin("alice", CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsTheAccountCounterButNotTheClientCounter() {
        throttle.recordLoginFailure("alice", CLIENT);
        throttle.recordLoginFailure("alice", CLIENT);
        throttle.recordLoginSuccess("alice", CLIENT);
        throttle.recordLoginFailure("alice", CLIENT);
        throttle.recordLoginFailure("alice", CLIENT);
        assertThatCode(() -> throttle.checkLogin("alice", CLIENT)).doesNotThrowAnyException();

        throttle.recordLoginFailure("bob", CLIENT);
        assertThatThrownBy(() -> throttle.checkLogin("carol", CLIENT))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void limitsRegistrationsPerClient() {
        throttle.acquireRegistration(CLIENT);
        throttle.acquireRegistration(CLIENT);

        assertThatThrownBy(() -> throttle.acquireRegistration(CLIENT))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.acquireRegistration(OTHER_CLIENT)).doesNotThrowAnyException();
        clock.advance(Duration.ofHours(1));
        assertThatCode(() -> throttle.acquireRegistration(CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void attemptLimiterDropsExpiredWindowsOnceItTracksManyKeys() {
        var limiter = new AttemptLimiter(1, Duration.ofMinutes(1), clock);
        for (var index = 0; index < AttemptLimiter.CLEANUP_THRESHOLD; index++) {
            limiter.record("key-" + index);
        }
        clock.advance(Duration.ofMinutes(1));

        limiter.record("fresh");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
