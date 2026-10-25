package com.sinai.media.internal;

import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.common.util.SrtTimeFormatter;
import com.sinai.media.api.SubtitleWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class SrtSubtitleWriter implements SubtitleWriter {

    @Override
    public void write(List<SubtitleSegment> segments, Path srtFile) {
        if (Objects.isNull(srtFile)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "SRT file path must not be null");
        }
        var content = render(segments);
        try {
            var parent = srtFile.toAbsolutePath().getParent();
            if (Objects.nonNull(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(srtFile, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.WORKSPACE_IO_ERROR, "Cannot write SRT file: " + srtFile, e);
        }
    }

    static String render(List<SubtitleSegment> segments) {
        if (CollectionUtils.isEmpty(segments)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Subtitle segments must not be empty");
        }
        var builder = new StringBuilder();
        for (var segment : segments) {
            builder.append(segment.index()).append('\n')
                    .append(SrtTimeFormatter.format(segment.startTimeMs()))
                    .append(" --> ")
                    .append(SrtTimeFormatter.format(segment.endTimeMs())).append('\n')
                    .append(segment.text()).append("\n\n");
        }
        return builder.toString();
    }
}
