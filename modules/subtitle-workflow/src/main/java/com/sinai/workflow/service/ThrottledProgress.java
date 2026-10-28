package com.sinai.workflow.service;

import com.sinai.ai.api.ProgressListener;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.IntConsumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ThrottledProgress implements ProgressListener {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(2);
    private static final int COMPLETE = 100;

    private final IntConsumer writer;
    private final Clock clock;
    private int lastWritten = -1;
    private Instant lastWrittenAt = Instant.MIN;

    ThrottledProgress(IntConsumer writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void onProgress(int percent) {
        var clamped = Math.clamp(percent, 0, COMPLETE);
        if (clamped <= lastWritten) {
            return;
        }
        var now = clock.instant();
        var isDue = !now.isBefore(lastWrittenAt.plus(MIN_INTERVAL));
        if (!isDue && clamped < COMPLETE && lastWritten >= 0) {
            return;
        }
        try {
            writer.accept(clamped);
            lastWritten = clamped;
            lastWrittenAt = now;
        } catch (RuntimeException e) {
            log.warn("Cannot record progress {}%", clamped, e);
        }
    }
}
