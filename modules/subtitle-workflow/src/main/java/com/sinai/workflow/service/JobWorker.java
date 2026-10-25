package com.sinai.workflow.service;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty(name = "sinai.workflow.worker.enabled", matchIfMissing = true)
public class JobWorker {

    private final JobLifecycleService lifecycle;
    private final JobPipeline pipeline;
    private final AtomicBoolean recovered = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${sinai.workflow.worker.poll-interval:5s}")
    public void poll() {
        try {
            recoverInterruptedJobsOnce();
            drainPendingJobs();
        } catch (RuntimeException e) {
            log.error("Job worker poll failed", e);
        }
    }

    private void recoverInterruptedJobsOnce() {
        if (recovered.get()) {
            return;
        }
        var interrupted = lifecycle.failInterruptedJobs();
        recovered.set(true);
        if (!CollectionUtils.isEmpty(interrupted)) {
            log.warn("Marked {} interrupted jobs as FAILED", interrupted.size());
        }
    }

    private void drainPendingJobs() {
        var claimed = lifecycle.claimNextPending();
        while (claimed.isPresent()) {
            var job = claimed.get();
            log.info("Processing job {}", job.id());
            pipeline.process(job);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            claimed = lifecycle.claimNextPending();
        }
    }
}
