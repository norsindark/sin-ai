package com.sinai.workflow.repository;

import com.sinai.common.constant.JobStatus;
import com.sinai.workflow.model.entity.SubtitleJob;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubtitleJobRepository extends JpaRepository<SubtitleJob, UUID> {

    @EntityGraph(attributePaths = {"video", "translationTasks"})
    Optional<SubtitleJob> findWithDetailsById(UUID id);

    @Query(value = "SELECT j FROM SubtitleJob j JOIN FETCH j.video",
            countQuery = "SELECT COUNT(j) FROM SubtitleJob j")
    Page<SubtitleJob> findPageWithVideo(Pageable pageable);

    @NativeQuery("SELECT CAST(j.id AS VARCHAR) FROM subtitle_jobs j "
            + "WHERE j.status = 'PENDING' "
            + "ORDER BY j.created_at, j.id "
            + "LIMIT 1 FOR UPDATE SKIP LOCKED")
    Optional<String> lockNextPendingId();

    @EntityGraph(attributePaths = {"video", "translationTasks"})
    List<SubtitleJob> findAllByStatus(JobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM SubtitleJob j JOIN FETCH j.video WHERE j.id = :id")
    Optional<SubtitleJob> findForUpdateById(@Param("id") UUID id);

    @Modifying
    @NativeQuery("UPDATE subtitle_jobs SET transcription_progress = :progress WHERE id = CAST(:jobId AS UUID)")
    int updateTranscriptionProgress(@Param("jobId") String jobId, @Param("progress") int progress);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @NativeQuery("DELETE FROM subtitle_jobs WHERE id = CAST(:jobId AS UUID)")
    int deleteJobRow(@Param("jobId") String jobId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @NativeQuery("DELETE FROM videos WHERE id = CAST(:videoId AS UUID)")
    int deleteVideoRow(@Param("videoId") String videoId);
}
