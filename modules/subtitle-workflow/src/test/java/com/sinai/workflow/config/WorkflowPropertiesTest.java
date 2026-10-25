package com.sinai.workflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesDefaults() {
        var properties = new WorkflowProperties(null, null, null);

        assertThat(properties.downloadUrlExpiry()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.tempDirectory())
                .isEqualTo(Path.of(System.getProperty("java.io.tmpdir"), "sin-ai").toAbsolutePath().normalize());
        assertThat(properties.uploadDirectory()).isEqualTo(properties.tempDirectory().resolve("uploads"));
    }

    @Test
    void normalizesConfiguredDirectories() {
        var properties = new WorkflowProperties(null, tempDir.resolve("a/../work"), tempDir.resolve("work/up"));

        assertThat(properties.tempDirectory()).isEqualTo(tempDir.resolve("work").toAbsolutePath().normalize());
        assertThat(properties.uploadDirectory()).isEqualTo(tempDir.resolve("work/up").toAbsolutePath().normalize());
    }

    @Test
    void rejectsUploadDirectoryOutsideTempDirectory() {
        var work = tempDir.resolve("work");

        assertThatThrownBy(() -> new WorkflowProperties(null, work, tempDir.resolve("elsewhere")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowProperties(null, work, work))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowProperties(null, work, work.resolve("../work-uploads")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
