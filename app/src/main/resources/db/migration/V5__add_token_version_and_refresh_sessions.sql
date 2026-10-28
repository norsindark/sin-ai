ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE refresh_sessions (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    used_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_refresh_sessions_token_hash ON refresh_sessions (token_hash);
CREATE INDEX ix_refresh_sessions_user_id ON refresh_sessions (user_id);
CREATE INDEX ix_refresh_sessions_expires_at ON refresh_sessions (expires_at);
