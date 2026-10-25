package com.sinai.media.internal;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class OutputPaths {

    private OutputPaths() {
    }

    static void createParentDirectory(Path output) {
        var parent = output.toAbsolutePath().getParent();
        if (Objects.isNull(parent)) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.MEDIA_PROCESSING_ERROR,
                    "Cannot create output directory: " + parent, e);
        }
    }
}
