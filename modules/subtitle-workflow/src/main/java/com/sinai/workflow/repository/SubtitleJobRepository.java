package com.sinai.workflow.repository;

import com.sinai.workflow.model.entity.SubtitleJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubtitleJobRepository extends JpaRepository<SubtitleJob, UUID> {

    @EntityGraph(attributePaths = {"video", "translationTasks"})
    Optional<SubtitleJob> findWithDetailsById(UUID id);

    @Query(value = "SELECT j FROM SubtitleJob j JOIN FETCH j.video",
            countQuery = "SELECT COUNT(j) FROM SubtitleJob j")
    Page<SubtitleJob> findPageWithVideo(Pageable pageable);
}
