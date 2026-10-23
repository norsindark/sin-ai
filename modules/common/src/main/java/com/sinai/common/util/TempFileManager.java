package com.sinai.common.util;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public class TempFileManager {

    private final Path baseDirectory;

    public TempFileManager(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory);
    }

    public TempFileManager() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "sin-ai"));
    }

    public TempWorkspace createWorkspace(UUID jobId) {
        Objects.requireNonNull(jobId);
        var directory = baseDirectory.resolve("job-" + jobId);
        try {
            Files.createDirectories(baseDirectory);
            Files.createDirectory(directory);
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.WORKSPACE_IO_ERROR, "Cannot create workspace: " + directory, e);
        }
        return new TempWorkspace(jobId, directory);
    }

    public TempWorkspace createWorkspace() {
        return createWorkspace(UUID.randomUUID());
    }
}
