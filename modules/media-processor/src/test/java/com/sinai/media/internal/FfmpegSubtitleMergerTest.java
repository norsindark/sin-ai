package com.sinai.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.media.api.SubtitleTrack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegSubtitleMergerTest {

    @Test
    void buildsExactArguments() {
        var arguments = FfmpegSubtitleMerger.buildArguments(Path.of("in.mp4"),
                List.of(new SubtitleTrack(Path.of("sub.srt"), Language.VIETNAMESE)), Path.of("out.mp4"));

        assertThat(arguments).containsExactly(
                "-i", "in.mp4", "-i", "sub.srt",
                "-map", "0:v", "-map", "0:a?", "-map", "1:0",
                "-c", "copy", "-c:s", "mov_text",
                "-metadata:s:s:0", "language=vie",
                "out.mp4");
    }

    @Test
    void buildsArgumentsForEveryTrackInOrder() {
        var arguments = FfmpegSubtitleMerger.buildArguments(Path.of("in.mp4"), List.of(
                new SubtitleTrack(Path.of("en.srt"), Language.ENGLISH),
                new SubtitleTrack(Path.of("vi.srt"), Language.VIETNAMESE),
                new SubtitleTrack(Path.of("ja.srt"), Language.JAPANESE)), Path.of("out.mp4"));

        assertThat(arguments).containsExactly(
                "-i", "in.mp4", "-i", "en.srt", "-i", "vi.srt", "-i", "ja.srt",
                "-map", "0:v", "-map", "0:a?", "-map", "1:0", "-map", "2:0", "-map", "3:0",
                "-c", "copy", "-c:s", "mov_text",
                "-metadata:s:s:0", "language=eng",
                "-metadata:s:s:1", "language=vie",
                "-metadata:s:s:2", "language=jpn",
                "out.mp4");
    }

    @Test
    void rejectsInvalidTrackLists(@TempDir Path dir) throws Exception {
        var video = Files.createFile(dir.resolve("v.mp4"));
        var srt = Files.createFile(dir.resolve("s.srt"));
        var out = dir.resolve("o.mp4");
        var valid = new SubtitleTrack(srt, Language.ENGLISH);

        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, null, out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, List.of(), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, Arrays.asList(valid, null), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video,
                List.of(valid, new SubtitleTrack(dir.resolve("missing.srt"), Language.VIETNAMESE)), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, List.of(valid, new SubtitleTrack(srt, null)), out));
        FfmpegSubtitleMerger.validate(video, List.of(valid, new SubtitleTrack(srt, Language.VIETNAMESE)), out);
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

        assertInvalid(() -> FfmpegSubtitleMerger.validate(null, track(srt, Language.ENGLISH), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(missing, track(srt, Language.ENGLISH), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, track(null, Language.ENGLISH), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, track(missing, Language.ENGLISH), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, track(srt, null), out));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, track(srt, Language.ENGLISH), null));
        FfmpegSubtitleMerger.validate(video, track(srt, Language.ENGLISH), out);
    }

    @Test
    void rejectsOutputEqualToInput(@TempDir Path dir) throws Exception {
        var video = Files.createFile(dir.resolve("v.mp4"));
        var srt = Files.createFile(dir.resolve("s.srt"));
        var sameViaDots = dir.resolve("sub").resolve("..").resolve("v.mp4");

        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, track(srt, Language.ENGLISH), video));
        assertInvalid(() -> FfmpegSubtitleMerger.validate(video, track(srt, Language.ENGLISH), sameViaDots));
    }

    private static List<SubtitleTrack> track(Path srtFile, Language language) {
        return List.of(new SubtitleTrack(srtFile, language));
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }
}
