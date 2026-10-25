package com.sinai.common.util;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class TempFileManager {

    private static final String WORKSPACE_PREFIX = "job-";
    private static final Pattern WORKSPACE_NAME = Pattern.compile(
            WORKSPACE_PREFIX + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final Path baseDirectory;

    public TempFileManager(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory);
    }

    public TempFileManager() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "sin-ai"));
    }

    public TempWorkspace createWorkspace(UUID jobId) {
        Objects.requireNonNull(jobId);
        var directory = baseDirectory.resolve(WORKSPACE_PREFIX + jobId);
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

    public List<Path> purgeStaleWorkspaces() {
        return purge(baseDirectory, TempFileManager::isWorkspaceDirectory);
    }

    public List<Path> purgeDirectoryContents(Path directory) {
        return purge(Objects.requireNonNull(directory), entry -> true);
    }

    private static List<Path> purge(Path directory, DirectoryStream.Filter<Path> filter) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        var purged = new ArrayList<Path>();
        var failures = new ArrayList<SinAiException>();
        try (var entries = Files.newDirectoryStream(directory, filter)) {
            for (var entry : entries) {
                try {
                    TempWorkspace.deleteRecursively(entry);
                    purged.add(entry);
                } catch (SinAiException e) {
                    failures.add(e);
                }
            }
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.WORKSPACE_IO_ERROR, "Cannot list directory: " + directory, e);
        }
        if (!failures.isEmpty()) {
            var exception = new SinAiException(ErrorCode.WORKSPACE_IO_ERROR,
                    "Cannot purge all entries in: " + directory);
            failures.forEach(exception::addSuppressed);
            throw exception;
        }
        return List.copyOf(purged);
    }

    private static boolean isWorkspaceDirectory(Path entry) {
        return WORKSPACE_NAME.matcher(entry.getFileName().toString()).matches()
                && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
    }
}
