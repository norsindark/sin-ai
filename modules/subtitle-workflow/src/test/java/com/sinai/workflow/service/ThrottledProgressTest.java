package com.sinai.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThrottledProgressTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-27T00:00:00Z"));
    private final List<Integer> written = new ArrayList<>();
    private final ThrottledProgress progress = new ThrottledProgress(written::add, clock);

    @Test
    void writesFirstValueThenAtMostOncePerInterval() {
        progress.onProgress(0);
        progress.onProgress(10);
        clock.advance(Duration.ofSeconds(1));
        progress.onProgress(20);
        clock.advance(ThrottledProgress.MIN_INTERVAL);
        progress.onProgress(30);

        assertThat(written).containsExactly(0, 30);
    }

    @Test
    void alwaysWritesCompletionAndIgnoresRegressions() {
        progress.onProgress(50);
        progress.onProgress(40);
        progress.onProgress(100);
        progress.onProgress(100);

        assertThat(written).containsExactly(50, 100);
    }

    @Test
    void clampsOutOfRangeValuesAndSurvivesWriterFailures() {
        var failing = new ThrottledProgress(percent -> {
            throw new IllegalStateException("db down");
        }, clock);
        failing.onProgress(10);

        progress.onProgress(150);

        assertThat(written).containsExactly(100);
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
