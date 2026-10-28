package com.sinai.identity.repository;

import com.sinai.identity.api.Role;
import com.sinai.identity.api.UserDirectory.UserCounts;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserStatsRepository {

    private final JdbcClient jdbcClient;

    public UserCounts countUsers(Instant newSince) {
        return jdbcClient.sql("SELECT COUNT(*) AS total, "
                        + "COUNT(*) FILTER (WHERE enabled) AS enabled, "
                        + "COUNT(*) FILTER (WHERE enabled AND role = :adminRole) AS active_admins, "
                        + "COUNT(*) FILTER (WHERE created_at >= :since) AS new_since "
                        + "FROM users")
                .param("adminRole", Role.ADMIN.name())
                .param("since", Timestamp.from(newSince))
                .query((resultSet, rowNumber) -> new UserCounts(resultSet.getLong("total"),
                        resultSet.getLong("enabled"), resultSet.getLong("active_admins"),
                        resultSet.getLong("new_since")))
                .single();
    }
}
