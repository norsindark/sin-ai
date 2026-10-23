package com.sinai.common.util;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;

public final class TempWorkspace implements AutoCloseable {

    private final UUID jobId;
    private final Path directory;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    TempWorkspace(UUID jobId, Path directory) {
        this.jobId = jobId;
        this.directory = directory.toAbsolutePath().normalize();
    }

    public UUID jobId() {
        return jobId;
    }

    public Path directory() {
        return directory;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public Path resolve(String fileName) {
        if (closed.get()) {
            throw new SinAiException(ErrorCode.WORKSPACE_IO_ERROR, "Workspace is closed: " + directory);
        }
        if (StringUtils.isBlank(fileName)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Blank file name");
        }
        try {
            var relative = Path.of(fileName);
            if (relative.isAbsolute()) {
                throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Absolute path not allowed: " + fileName);
            }
            var resolved = directory.resolve(relative).normalize();
            if (!resolved.startsWith(directory) || resolved.equals(directory)) {
                throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Path escapes workspace: " + fileName);
            }
            return resolved;
        } catch (InvalidPathException e) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Invalid path: " + fileName, e);
        }
    }

    public Path createSubdirectory(String name) {
        var target = resolve(name);
        try {
            return Files.createDirectories(target);
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.WORKSPACE_IO_ERROR, "Cannot create directory: " + target, e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var failures = new ArrayList<IOException>();
        try {
            Files.walkFileTree(directory, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            deleteCollecting(file, failures);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            failures.add(exc);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                            if (exc != null) {
                                failures.add(exc);
                            }
                            deleteCollecting(dir, failures);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            failures.add(e);
        }
        if (!failures.isEmpty()) {
            var exception = new SinAiException(ErrorCode.WORKSPACE_IO_ERROR,
                    "Cannot fully delete workspace: " + directory);
            failures.forEach(exception::addSuppressed);
            throw exception;
        }
    }

    private static void deleteCollecting(Path path, List<IOException> failures) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            failures.add(e);
        }
    }
}
