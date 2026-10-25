package com.sinai.storage.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import io.minio.MinioClient;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinioObjectStorageTest {

    private final MinioClient client = MinioClient.builder()
            .endpoint("http://localhost:1")
            .credentials("a", "b")
            .build();
    private final MinioObjectStorage storage = new MinioObjectStorage(client);

    @TempDir
    Path tempDir;

    @Test
    void uploadRejectsBlankBucket() {
        assertThatThrownBy(() -> storage.upload(" ", "k", tempDir.resolve("x"), "video/mp4"))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void uploadRejectsBlankKey() {
        assertThatThrownBy(() -> storage.upload("b", "", tempDir.resolve("x"), "video/mp4"))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void uploadRejectsMissingFile() {
        assertThatThrownBy(() -> storage.upload("b", "k", tempDir.resolve("missing.mp4"), "video/mp4"))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void uploadRejectsNullFile() {
        assertThatThrownBy(() -> storage.upload("b", "k", null, "video/mp4"))
                .isInstanceOf(SinAiException.class);
    }

    @Test
    void downloadRejectsBlankKeyAndNullTarget() {
        assertThatThrownBy(() -> storage.download("b", " ", tempDir.resolve("t")))
                .isInstanceOf(SinAiException.class);
        assertThatThrownBy(() -> storage.download("b", "k", null))
                .isInstanceOf(SinAiException.class);
    }

    @Test
    void presignRejectsExpiryBeyondSevenDays() {
        assertThatThrownBy(() -> storage.presignedGetUrl("b", "k", Duration.ofDays(7).plusSeconds(1)))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void presignRejectsInvalidArguments() {
        assertThatThrownBy(() -> storage.presignedGetUrl(null, "k", Duration.ofMinutes(1)))
                .isInstanceOf(SinAiException.class);
        assertThatThrownBy(() -> storage.presignedGetUrl("b", "k", null))
                .isInstanceOf(SinAiException.class);
        assertThatThrownBy(() -> storage.presignedGetUrl("b", "k", Duration.ZERO))
                .isInstanceOf(SinAiException.class);
    }
}
