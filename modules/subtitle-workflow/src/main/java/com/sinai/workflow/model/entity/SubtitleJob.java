package com.sinai.workflow.model.entity;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subtitle_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubtitleJob {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "video_id", nullable = false, updatable = false)
    private Video video;

    @Column(name = "source_language", nullable = false, updatable = false)
    private Language sourceLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "source_subtitle_key")
    private String sourceSubtitleKey;

    @Column(name = "output_video_key")
    private String outputVideoKey;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "transcription_progress", insertable = false, updatable = false)
    private Integer transcriptionProgress;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("targetLanguage")
    private List<TranslationTask> translationTasks = new ArrayList<>();

    public static SubtitleJob create(Video video, Language sourceLanguage, Collection<Language> targetLanguages) {
        var job = new SubtitleJob();
        job.id = UUID.randomUUID();
        job.video = video;
        job.sourceLanguage = sourceLanguage;
        job.status = JobStatus.PENDING;
        for (var targetLanguage : targetLanguages) {
            job.translationTasks.add(new TranslationTask(UUID.randomUUID(), job, targetLanguage));
        }
        return job;
    }

    public void start() {
        requireStatus(JobStatus.PENDING);
        status = JobStatus.PROCESSING;
        startedAt = Instant.now();
    }

    public void attachSourceSubtitle(String subtitleKey) {
        requireStatus(JobStatus.PROCESSING);
        sourceSubtitleKey = subtitleKey;
    }

    public void complete(String videoKey) {
        requireStatus(JobStatus.PROCESSING);
        outputVideoKey = videoKey;
        status = JobStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void fail(ErrorCode failure) {
        requireStatus(JobStatus.PROCESSING);
        status = JobStatus.FAILED;
        errorCode = failure.name();
        errorMessage = failure.getDefaultMessage();
        completedAt = Instant.now();
        for (var task : translationTasks) {
            if (!task.getStatus().isTerminal()) {
                task.fail(failure.getDefaultMessage());
            }
        }
    }

    private void requireStatus(JobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Job " + id + " is " + status + ", expected " + expected);
        }
    }
}
