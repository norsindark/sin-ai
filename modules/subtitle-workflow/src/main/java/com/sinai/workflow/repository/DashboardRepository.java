package com.sinai.workflow.repository;

import com.sinai.common.constant.JobStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DashboardRepository {

    private final JdbcClient jdbcClient;

    public Map<JobStatus, Long> countJobsByStatus() {
        return jdbcClient.sql("SELECT status, COUNT(*) AS job_count FROM subtitle_jobs GROUP BY status")
                .query((resultSet, rowNumber) -> Map.entry(
                        EnumUtils.getEnum(JobStatus.class, resultSet.getString("status")),
                        resultSet.getLong("job_count")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<LocalDate, Long> countJobsPerUtcDay(Instant since) {
        return jdbcClient.sql("SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day, COUNT(*) AS job_count "
                        + "FROM subtitle_jobs WHERE created_at >= :since GROUP BY day")
                .param("since", Timestamp.from(since))
                .query((resultSet, rowNumber) -> Map.entry(resultSet.getDate("day").toLocalDate(),
                        resultSet.getLong("job_count")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public StoredVideos sumStoredVideos() {
        return jdbcClient.sql("SELECT COUNT(*) AS video_count, COALESCE(SUM(size_bytes), 0) AS total_bytes "
                        + "FROM videos")
                .query((resultSet, rowNumber) -> new StoredVideos(resultSet.getLong("video_count"),
                        resultSet.getLong("total_bytes")))
                .single();
    }

    public List<RecentJobRow> findRecentJobs(int limit) {
        return jdbcClient.sql("SELECT CAST(j.id AS VARCHAR) AS id, v.title, j.status, "
                        + "CAST(j.owner_id AS VARCHAR) AS owner_id, j.created_at "
                        + "FROM subtitle_jobs j JOIN videos v ON v.id = j.video_id "
                        + "ORDER BY j.created_at DESC, j.id DESC LIMIT :limit")
                .param("limit", limit)
                .query((resultSet, rowNumber) -> {
                    var ownerId = resultSet.getString("owner_id");
                    return new RecentJobRow(UUID.fromString(resultSet.getString("id")), resultSet.getString("title"),
                            EnumUtils.getEnum(JobStatus.class, resultSet.getString("status")),
                            Objects.isNull(ownerId) ? null : UUID.fromString(ownerId),
                            resultSet.getTimestamp("created_at").toInstant());
                })
                .list();
    }

    public record StoredVideos(long count, long totalBytes) {
    }

    public record RecentJobRow(UUID id, String title, JobStatus status, UUID ownerId, Instant createdAt) {
    }
}
