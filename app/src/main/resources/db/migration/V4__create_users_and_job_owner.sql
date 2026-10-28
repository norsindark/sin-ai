CREATE TABLE users (
    id            UUID          PRIMARY KEY,
    username      VARCHAR(32)   NOT NULL,
    email         VARCHAR(254)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    role          VARCHAR(16)   NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_users_username_format CHECK (username ~ '^[a-z0-9._-]{3,32}$'),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE UNIQUE INDEX uq_users_username ON users (username);
CREATE UNIQUE INDEX uq_users_email ON users (email);
CREATE INDEX ix_users_created_at ON users (created_at DESC, id DESC);

CREATE TABLE revoked_tokens (
    jti        UUID         PRIMARY KEY,
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_revoked_tokens_expires_at ON revoked_tokens (expires_at);

ALTER TABLE subtitle_jobs ADD COLUMN owner_id UUID;
ALTER TABLE subtitle_jobs ADD CONSTRAINT fk_subtitle_jobs_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX ix_subtitle_jobs_owner_created_at ON subtitle_jobs (owner_id, created_at DESC, id DESC);
