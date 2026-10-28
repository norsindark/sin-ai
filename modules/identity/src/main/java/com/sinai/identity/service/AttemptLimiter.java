package com.sinai.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AttemptLimiter {

    static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public Optional<Duration> blockedFor(String key) {
        var now = clock.instant();
        var current = windows.get(key);
        if (Objects.isNull(current) || current.isExpired(now, window) || current.attempts() < maxAttempts) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, current.startedAt().plus(window)));
    }

    public void record(String key) {
        var now = clock.instant();
        if (windows.size() >= CLEANUP_THRESHOLD) {
            windows.values().removeIf(existing -> existing.isExpired(now, window));
        }
        windows.compute(key, (ignored, current) -> Objects.isNull(current) || current.isExpired(now, window)
                ? new Window(now, 1)
                : new Window(current.startedAt(), current.attempts() + 1));
    }

    public void reset(String key) {
        windows.remove(key);
    }

    int trackedKeys() {
        return windows.size();
    }

    private record Window(Instant startedAt, int attempts) {

        boolean isExpired(Instant now, Duration window) {
            return !now.isBefore(startedAt.plus(window));
        }
    }
}
