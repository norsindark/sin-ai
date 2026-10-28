ALTER TABLE subtitle_jobs ADD COLUMN transcription_progress SMALLINT;
ALTER TABLE subtitle_jobs ADD CONSTRAINT ck_subtitle_jobs_transcription_progress
    CHECK (transcription_progress BETWEEN 0 AND 100);

ALTER TABLE translation_tasks ADD COLUMN progress SMALLINT;
ALTER TABLE translation_tasks ADD CONSTRAINT ck_translation_tasks_progress
    CHECK (progress BETWEEN 0 AND 100);

UPDATE subtitle_jobs SET transcription_progress = 100 WHERE source_subtitle_key IS NOT NULL;
UPDATE translation_tasks SET progress = 100 WHERE status = 'COMPLETED';
