package com.sinai.identity.repository;

import com.sinai.identity.api.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TokenStateRepository {

    private final JdbcClient jdbcClient;

    public Optional<TokenSubjectState> findSubjectState(UUID userId, UUID jti) {
        return jdbcClient.sql("SELECT u.role, u.enabled, u.token_version, "
                        + "EXISTS (SELECT 1 FROM revoked_tokens r WHERE r.jti = CAST(:jti AS UUID)) AS revoked "
                        + "FROM users u WHERE u.id = CAST(:userId AS UUID)")
                .param("jti", jti.toString())
                .param("userId", userId.toString())
                .query((resultSet, rowNumber) -> new TokenSubjectState(
                        EnumUtils.getEnum(Role.class, resultSet.getString("role")),
                        resultSet.getBoolean("enabled"),
                        resultSet.getInt("token_version"),
                        resultSet.getBoolean("revoked")))
                .optional();
    }

    public void revoke(UUID jti, Instant expiresAt) {
        jdbcClient.sql("INSERT INTO revoked_tokens (jti, expires_at) VALUES (CAST(:jti AS UUID), :expiresAt) "
                        + "ON CONFLICT (jti) DO NOTHING")
                .param("jti", jti.toString())
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
    }

    public int deleteExpired(Instant now) {
        return jdbcClient.sql("DELETE FROM revoked_tokens WHERE expires_at < :now")
                .param("now", Timestamp.from(now))
                .update();
    }

    public record TokenSubjectState(Role role, boolean enabled, int tokenVersion, boolean revoked) {
    }
}
