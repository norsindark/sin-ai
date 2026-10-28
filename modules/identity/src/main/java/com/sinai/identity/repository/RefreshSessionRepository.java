package com.sinai.identity.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshSessionRepository {

    private final JdbcClient jdbcClient;

    public void insert(UUID sessionId, UUID userId, String tokenHash, Instant expiresAt) {
        jdbcClient.sql("INSERT INTO refresh_sessions (id, user_id, token_hash, expires_at) "
                        + "VALUES (CAST(:id AS UUID), CAST(:userId AS UUID), :tokenHash, :expiresAt)")
                .param("id", sessionId.toString())
                .param("userId", userId.toString())
                .param("tokenHash", tokenHash)
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
    }

    public Optional<RefreshSession> lockActiveByTokenHash(String tokenHash, Instant now) {
        return jdbcClient.sql("SELECT CAST(id AS VARCHAR) AS id, CAST(user_id AS VARCHAR) AS user_id, expires_at "
                        + "FROM refresh_sessions WHERE token_hash = :tokenHash AND expires_at > :now FOR UPDATE")
                .param("tokenHash", tokenHash)
                .param("now", Timestamp.from(now))
                .query((resultSet, rowNumber) -> new RefreshSession(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("user_id")),
                        resultSet.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    public void rotate(UUID sessionId, String newTokenHash, Instant usedAt) {
        jdbcClient.sql("UPDATE refresh_sessions SET token_hash = :tokenHash, used_at = :usedAt "
                        + "WHERE id = CAST(:id AS UUID)")
                .param("tokenHash", newTokenHash)
                .param("usedAt", Timestamp.from(usedAt))
                .param("id", sessionId.toString())
                .update();
    }

    public int deleteByTokenHash(String tokenHash) {
        return jdbcClient.sql("DELETE FROM refresh_sessions WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .update();
    }

    public int deleteByUserId(UUID userId) {
        return jdbcClient.sql("DELETE FROM refresh_sessions WHERE user_id = CAST(:userId AS UUID)")
                .param("userId", userId.toString())
                .update();
    }

    public int deleteExpired(Instant now) {
        return jdbcClient.sql("DELETE FROM refresh_sessions WHERE expires_at <= :now")
                .param("now", Timestamp.from(now))
                .update();
    }

    public record RefreshSession(UUID id, UUID userId, Instant expiresAt) {
    }
}
