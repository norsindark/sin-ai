package com.sinai.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SrtSubtitleWriterTest {

    private final SrtSubtitleWriter writer = new SrtSubtitleWriter();

    @Test
    void rendersExactSrtForTwoSegments() {
        var segments = List.of(
                new SubtitleSegment(1, 0, 1500, "Xin chào thế giới"),
                new SubtitleSegment(2, 3_723_004, 3_725_000, "Tạm biệt"));

        var rendered = SrtSubtitleWriter.render(segments);

        assertThat(rendered).isEqualTo(
                "1\n00:00:00,000 --> 00:00:01,500\nXin chào thế giới\n\n"
                        + "2\n01:02:03,004 --> 01:02:05,000\nTạm biệt\n\n");
    }

    @Test
    void writesUtf8WithoutBom(@TempDir Path dir) throws Exception {
        var file = dir.resolve("nested/out.srt");
        var segments = List.of(new SubtitleSegment(1, 0, 1000, "Tiếng Việt"));

        writer.write(segments, file);

        var bytes = Files.readAllBytes(file);
        var hasBom = bytes.length >= 3
                && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
        assertThat(hasBom).isFalse();
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo("1\n00:00:00,000 --> 00:00:01,000\nTiếng Việt\n\n");
    }

    @Test
    void rejectsEmptyAndNullList(@TempDir Path dir) {
        var file = dir.resolve("out.srt");

        assertThatThrownBy(() -> writer.write(List.of(), file))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> writer.write(null, file))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }
}
