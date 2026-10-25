CREATE TABLE videos (
    id                UUID          PRIMARY KEY,
    original_filename VARCHAR(255)  NOT NULL,
    content_type      VARCHAR(100)  NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    storage_bucket    VARCHAR(63)   NOT NULL,
    storage_key       VARCHAR(1024) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_videos_size_positive CHECK (size_bytes > 0),
    CONSTRAINT uq_videos_storage_location UNIQUE (storage_bucket, storage_key)
);

CREATE TABLE subtitle_jobs (
    id                  UUID          PRIMARY KEY,
    video_id            UUID          NOT NULL,
    source_language     VARCHAR(8)    NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    source_subtitle_key VARCHAR(1024),
    output_video_key    VARCHAR(1024),
    error_code          VARCHAR(64),
    error_message       TEXT,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    CONSTRAINT fk_subtitle_jobs_video FOREIGN KEY (video_id) REFERENCES videos (id),
    CONSTRAINT ck_subtitle_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX ix_subtitle_jobs_status_created_at ON subtitle_jobs (status, created_at);
CREATE INDEX ix_subtitle_jobs_created_at ON subtitle_jobs (created_at DESC);
CREATE INDEX ix_subtitle_jobs_video_id ON subtitle_jobs (video_id);

CREATE TABLE translation_tasks (
    id              UUID          PRIMARY KEY,
    job_id          UUID          NOT NULL,
    target_language VARCHAR(8)    NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    subtitle_key    VARCHAR(1024),
    error_message   TEXT,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_translation_tasks_job FOREIGN KEY (job_id) REFERENCES subtitle_jobs (id) ON DELETE CASCADE,
    CONSTRAINT ck_translation_tasks_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT uq_translation_tasks_job_language UNIQUE (job_id, target_language)
);
