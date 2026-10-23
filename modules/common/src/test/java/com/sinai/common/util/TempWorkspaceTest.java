package com.sinai.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TempWorkspaceTest {

    @TempDir
    Path base;

    private TempFileManager manager() {
        return new TempFileManager(base);
    }

    private static void assertCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(SinAiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void createsWorkspaceForJobId() {
        var jobId = UUID.randomUUID();
        try (var workspace = manager().createWorkspace(jobId)) {
            assertThat(workspace.jobId()).isEqualTo(jobId);
            assertThat(workspace.directory()).isEqualTo(base.resolve("job-" + jobId).toAbsolutePath().normalize());
            assertThat(workspace.directory()).isDirectory();
            assertThat(workspace.isClosed()).isFalse();
        }
    }

    @Test
    void randomWorkspacesAreDistinct() {
        try (var first = manager().createWorkspace(); var second = manager().createWorkspace()) {
            assertThat(first.directory()).isNotEqualTo(second.directory());
        }
    }

    @Test
    void duplicateJobIdFails() {
        var jobId = UUID.randomUUID();
        try (var ignored = manager().createWorkspace(jobId)) {
            assertThatThrownBy(() -> manager().createWorkspace(jobId))
                    .satisfies(e -> assertCode(e, ErrorCode.WORKSPACE_IO_ERROR));
        }
    }

    @Test
    void createsMissingNestedBase() {
        var nested = base.resolve("a").resolve("b");
        try (var workspace = new TempFileManager(nested).createWorkspace()) {
            assertThat(workspace.directory()).isDirectory().startsWith(nested.toAbsolutePath());
        }
    }

    @ParameterizedTest
    @NullSource
    void nullJobIdFails(UUID jobId) {
        assertThatThrownBy(() -> manager().createWorkspace(jobId)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolveReturnsPathWithoutCreating() {
        try (var workspace = manager().createWorkspace()) {
            var resolved = workspace.resolve("audio.wav");
            assertThat(resolved).isEqualTo(workspace.directory().resolve("audio.wav"));
            assertThat(resolved).doesNotExist();
        }
    }

    @Test
    void resolveAllowsNestedPath() {
        try (var workspace = manager().createWorkspace()) {
            assertThat(workspace.resolve("segments/part-1.srt").startsWith(workspace.directory())).isTrue();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "../escape.txt", "a/../../x", "."})
    void resolveRejectsInvalid(String name) {
        try (var workspace = manager().createWorkspace()) {
            assertThatThrownBy(() -> workspace.resolve(name))
                    .satisfies(e -> assertCode(e, ErrorCode.INVALID_ARGUMENT));
        }
    }

    @Test
    void resolveRejectsAbsolute() {
        var absolute = base.toAbsolutePath().getRoot().resolve("outside.txt").toString();
        try (var workspace = manager().createWorkspace()) {
            assertThatThrownBy(() -> workspace.resolve(absolute))
                    .satisfies(e -> assertCode(e, ErrorCode.INVALID_ARGUMENT));
        }
    }

    @Test
    void createSubdirectoryCreatesIt() {
        try (var workspace = manager().createWorkspace()) {
            var created = workspace.createSubdirectory("segments/inner");
            assertThat(created).isDirectory().startsWith(workspace.directory());
        }
    }

    @Test
    void resolveAfterCloseFails() {
        var workspace = manager().createWorkspace();
        workspace.close();
        assertThatThrownBy(() -> workspace.resolve("a.txt"))
                .satisfies(e -> assertCode(e, ErrorCode.WORKSPACE_IO_ERROR));
    }

    @Test
    void closeDeletesNestedContent() throws IOException {
        var workspace = manager().createWorkspace();
        var nested = workspace.createSubdirectory("x/y");
        Files.writeString(nested.resolve("f.txt"), "data");
        Files.writeString(workspace.resolve("root.txt"), "data");
        workspace.close();
        assertThat(Files.notExists(workspace.directory())).isTrue();
    }

    @Test
    void closeTwiceIsNoOp() {
        var workspace = manager().createWorkspace();
        workspace.close();
        workspace.close();
        assertThat(workspace.isClosed()).isTrue();
    }

    @Test
    void tryWithResourcesDeletesWhenBodyThrows() {
        var holder = new Path[1];
        assertThatThrownBy(() -> {
            try (var workspace = manager().createWorkspace()) {
                holder[0] = workspace.directory();
                throw new IllegalStateException("boom");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(Files.notExists(holder[0])).isTrue();
    }

    @Test
    void closeIsNoOpWhenDirectoryDeletedExternally() throws IOException {
        var workspace = manager().createWorkspace();
        Files.delete(workspace.directory());
        workspace.close();
        assertThat(workspace.isClosed()).isTrue();
    }

    @Test
    void closeDoesNotFollowSymlinks() throws IOException {
        var outside = base.resolve("outside.txt");
        Files.writeString(outside, "keep");
        var workspace = manager().createWorkspace();
        var link = workspace.directory().resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("Symbolic links unavailable");
        }
        workspace.close();
        assertThat(Files.notExists(workspace.directory())).isTrue();
        assertThat(outside).exists().hasContent("keep");
    }
}
