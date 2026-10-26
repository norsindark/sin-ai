ALTER TABLE videos ADD COLUMN title VARCHAR(255);

UPDATE videos
SET title = COALESCE(NULLIF(BTRIM(REGEXP_REPLACE(original_filename, '\.mp4$', '', 'i')), ''), original_filename);

ALTER TABLE videos ALTER COLUMN title SET NOT NULL;
