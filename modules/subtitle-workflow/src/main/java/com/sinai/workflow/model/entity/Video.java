package com.sinai.workflow.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "videos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video {

    @Id
    private UUID id;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_bucket", nullable = false)
    private String storageBucket;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Video(UUID id, String originalFilename, String contentType, long sizeBytes, String storageBucket,
            String storageKey) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.title = defaultTitle(originalFilename);
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageBucket = storageBucket;
        this.storageKey = storageKey;
    }

    public void rename(String newTitle) {
        title = StringUtils.trim(newTitle);
    }

    public static String defaultTitle(String filename) {
        var withoutExtension = StringUtils.trim(Strings.CI.removeEnd(StringUtils.trim(filename), ".mp4"));
        return StringUtils.defaultIfBlank(withoutExtension, StringUtils.trim(filename));
    }
}
