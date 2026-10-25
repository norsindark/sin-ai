package com.sinai.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegSubtitleMergerTest {

    @Test
    void buildsExactArguments() {
        var arguments = FfmpegSubtitleMerger.buildArguments(
                Path.of("in.mp4"), Path.of("sub.srt"), Language.VIETNAMESE, Path.of("out.mp4"));

        assertThat(arguments).containsExactly(
                "-i", "in.mp4", "-i", "sub.srt",
                "-map", "0:v", "-map", "0:a?", "-map", "1:0",
                "-c", "copy", "-c:s", "mov_text",
                "-metadata:s:s:0", "language=vie",
                "out.mp4");
    }

    @Test
    void mapsEveryLanguageToIso6392() {
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.ENGLISH)).isEqualTo("eng");
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.VIETNAMESE)).isEqualTo("vie");
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.JAPANESE)).isEqualTo("jpn");
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.KOREAN)).isEqualTo("kor");
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.CHINESE)).isEqualTo("zho");
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.FRENCH)).isEqualTo("fra");
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.GERMAN)).isEqualTo("deu");
        assertThat(FfmpegSubtitleMerger.toIso6392(Language.SPANISH)).isEqualTo("spa");
        for (var language : Language.values()) {
            assertThat(FfmpegSubtitleMerger.toIso6392(language)).hasSize(3);
        }
    }

    @Test
    void rejectsInvalidInputs(@TempDir Path dir) throws Exception {
        var video = Files.createFile(dir.resolve("v.mp4"));
        var srt = Files.createFile(dir.resolve("s.srt"));
        var out = dir.resolve("o.mp4");
        var missing = dir.resolve("missing");

        assertInvalid(() -> FfmpegSubtitleMerger.validate(null, srt, Language.ENGLISH, out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(missing, srt, Language.ENGLISH, out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, null, Language.ENGLISH, out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, missing, Language.ENGLISH, out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, srt, null, out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, srt, Language.ENGLISH, null));
        FfmpegSubtitleMerger.validate(video, srt, Language.ENGLISH, out);
    }

    @Test
    void rejectsOutputEqualToInput(@TempDir Path dir) throws Exception {
        var video = Files.createFile(dir.resolve("v.mp4"));
        var srt = Files.createFile(dir.resolve("s.srt"));
        var sameViaDots = dir.resolve("sub").resolve("..").resolve("v.mp4");

        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, srt, Language.ENGLISH, video));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, srt, Language.ENGLISH, sameViaDots));
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }
}
