package com.sinai.workflow.repository;

import com.sinai.workflow.model.entity.TranslationTask;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

public interface TranslationTaskRepository extends JpaRepository<TranslationTask, UUID> {

    @NativeQuery("SELECT t.* FROM translation_tasks t "
            + "WHERE t.job_id = ANY(CAST(:jobIds AS UUID[])) "
            + "ORDER BY t.job_id, t.target_language")
    List<TranslationTask> findAllByJobIds(@Param("jobIds") String[] jobIds);
}
