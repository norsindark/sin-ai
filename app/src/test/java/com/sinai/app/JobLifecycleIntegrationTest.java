package com.sinai.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.workflow.model.entity.SubtitleJob;
import com.sinai.workflow.model.entity.Video;
import com.sinai.workflow.repository.SubtitleJobRepository;
import com.sinai.workflow.service.JobLifecycleService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"sinai.workflow.worker.enabled=false",
        "sinai.workflow.temp-directory=${java.io.tmpdir}/sin-ai-test"})
@EnabledIf("com.sinai.app.JobFlowIntegrationTest#infrastructureAvailable")
class JobLifecycleIntegrationTest {

    @Autowired
    private JobLifecycleService lifecycle;

    @Autowired
    private SubtitleJobRepository jobRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdJobIds = new ArrayList<>();

    @BeforeEach
    void requireNoForeignActiveJobs() {
        var active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subtitle_jobs WHERE status IN ('PENDING', 'PROCESSING')", Long.class);
        Assumptions.assumeTrue(active == 0, "Database has active jobs that this test must not touch");
    }

    @AfterEach
    void cleanUp() {
        for (var jobId : createdJobIds) {
            var videoId = jdbcTemplate.queryForObject(
                    "SELECT CAST(video_id AS VARCHAR) FROM subtitle_jobs WHERE id = CAST(? AS UUID)", String.class,
                    jobId.toString());
            jdbcTemplate.update("DELETE FROM subtitle_jobs WHERE id = CAST(? AS UUID)", jobId.toString());
            jdbcTemplate.update("DELETE FROM videos WHERE id = CAST(? AS UUID)", videoId);
        }
    }

    @Test
    void claimsOldestPendingJobOnceWithSnapshot() {
        var first = createPendingJob(Language.VIETNAMESE, Language.JAPANESE);
        var second = createPendingJob(Language.KOREAN);

        var claimed = lifecycle.claimNextPending().orElseThrow();

        assertThat(claimed.id()).isEqualTo(first);
        assertThat(claimed.sourceLanguage()).isEqualTo(Language.ENGLISH);
        var stored = jobRepository.findWithDetailsById(first).orElseThrow();
        assertThat(claimed.videoBucket()).isEqualTo("raw-videos");
        assertThat(claimed.videoKey()).isEqualTo(videoKey(stored.getVideo().getId()));
        assertThat(claimed.tasks()).extracting(task -> task.targetLanguage())
                .containsExactly(Language.JAPANESE, Language.VIETNAMESE);
        assertThat(stored.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(stored.getStartedAt()).isNotNull();
        assertThat(lifecycle.claimNextPending().orElseThrow().id()).isEqualTo(second);
        assertThat(lifecycle.claimNextPending()).isEmpty();
    }

    @Test
    void lockedPendingJobIsSkippedByConcurrentClaim() throws Exception {
        var locked = createPendingJob(Language.VIETNAMESE);
        var free = createPendingJob(Language.JAPANESE);
        var lockAcquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var lockedId = new AtomicReference<String>();
        var holder = Thread.ofVirtual().start(() -> transactionTemplate.executeWithoutResult(status -> {
            lockedId.set(jobRepository.lockNextPendingId().orElseThrow());
            lockAcquired.countDown();
            awaitQuietly(release);
        }));
        try {
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(lockedId.get()).isEqualTo(locked.toString());

            assertThat(lifecycle.claimNextPending().orElseThrow().id()).isEqualTo(free);
        } finally {
            release.countDown();
            holder.join(10_000);
        }
        assertThat(jobRepository.findById(locked).orElseThrow().getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void completesTasksAndJobThroughLifecycle() {
        var jobId = createPendingJob(Language.VIETNAMESE);
        var claimed = lifecycle.claimNextPending().orElseThrow();
        var taskId = claimed.tasks().getFirst().id();

        lifecycle.attachSourceSubtitle(jobId, "jobs/" + jobId + "/subtitles/en.srt");
        lifecycle.startTask(taskId);
        lifecycle.completeTask(taskId, "jobs/" + jobId + "/subtitles/vi.srt");
        lifecycle.complete(jobId, "jobs/" + jobId + "/output.mp4");

        var stored = jobRepository.findWithDetailsById(jobId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(stored.getCompletedAt()).isNotNull();
        assertThat(stored.getSourceSubtitleKey()).endsWith("/subtitles/en.srt");
        assertThat(stored.getOutputVideoKey()).endsWith("/output.mp4");
        assertThat(stored.getTranslationTasks()).singleElement().satisfies(task -> {
            assertThat(task.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(task.getSubtitleKey()).endsWith("/subtitles/vi.srt");
        });
    }

    @Test
    void interruptedJobsAndTheirOpenTasksBecomeFailed() {
        var jobId = createPendingJob(Language.VIETNAMESE, Language.JAPANESE);
        var claimed = lifecycle.claimNextPending().orElseThrow();
        lifecycle.startTask(claimed.tasks().getFirst().id());
        var untouched = createPendingJob(Language.KOREAN);

        assertThat(lifecycle.failInterruptedJobs()).containsExactly(jobId);

        var stored = jobRepository.findWithDetailsById(jobId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(stored.getErrorCode()).isEqualTo(ErrorCode.JOB_INTERRUPTED.name());
        assertThat(stored.getErrorMessage()).isEqualTo(ErrorCode.JOB_INTERRUPTED.getDefaultMessage());
        assertThat(stored.getTranslationTasks()).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(task.getErrorMessage()).isEqualTo(ErrorCode.JOB_INTERRUPTED.getDefaultMessage());
        });
        assertThat(jobRepository.findById(untouched).orElseThrow().getStatus()).isEqualTo(JobStatus.PENDING);
    }

    private UUID createPendingJob(Language... targets) {
        var videoId = UUID.randomUUID();
        var video = new Video(videoId, "clip.mp4", "video/mp4", 16, "raw-videos", videoKey(videoId));
        var jobId = jobRepository.save(SubtitleJob.create(video, Language.ENGLISH, List.of(targets))).getId();
        createdJobIds.add(jobId);
        return jobId;
    }

    private static String videoKey(UUID videoId) {
        return "it/" + videoId + "/source.mp4";
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
