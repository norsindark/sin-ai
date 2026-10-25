package com.sinai.workflow.model.entity;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.constant.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "translation_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationTask {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private SubtitleJob job;

    @Column(name = "target_language", nullable = false, updatable = false)
    private Language targetLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "subtitle_key")
    private String subtitleKey;

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

    TranslationTask(UUID id, SubtitleJob job, Language targetLanguage) {
        this.id = id;
        this.job = job;
        this.targetLanguage = targetLanguage;
        this.status = JobStatus.PENDING;
    }

    public void start() {
        requireStatus(JobStatus.PENDING);
        status = JobStatus.PROCESSING;
    }

    public void complete(String key) {
        requireStatus(JobStatus.PROCESSING);
        subtitleKey = key;
        status = JobStatus.COMPLETED;
    }

    public void fail(String message) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Translation task " + id + " is already " + status);
        }
        status = JobStatus.FAILED;
        errorMessage = message;
    }

    private void requireStatus(JobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Translation task " + id + " is " + status + ", expected " + expected);
        }
    }
}
