package com.sinai.media.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.media.api.SubtitleTrack;
import com.sinai.media.config.MediaProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegMediaIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsAudioAndMergesSubtitleWithRealFfmpeg() {
        Assumptions.assumeTrue(isRunnable("ffmpeg"), "ffmpeg is not available");
        Assumptions.assumeTrue(isRunnable("ffprobe"), "ffprobe is not available");
        var runner = new FfmpegRunner(new MediaProperties("ffmpeg", Duration.ofSeconds(60)));
        var extractor = new FfmpegAudioExtractor(runner);
        var writer = new SrtSubtitleWriter();
        var merger = new FfmpegSubtitleMerger(runner);
        var fixture = tempDir.resolve("fixture.mp4");
        runner.run(List.of(
                "-f", "lavfi", "-i", "testsrc=d=2:size=160x120:rate=10",
                "-f", "lavfi", "-i", "sine=d=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-shortest",
                fixture.toString()));

        var wav = tempDir.resolve("audio.wav");
        extractor.extractAudio(fixture, wav);

        var audioInfo = probe(wav, "-show_entries", "stream=sample_rate,channels,codec_name", "-of", "csv=p=0");
        assertThat(audioInfo).isEqualTo("pcm_s16le,16000,1");

        var srt = tempDir.resolve("subtitles.srt");
        writer.write(List.of(
                new SubtitleSegment(1, 0, 900, "Xin chào"),
                new SubtitleSegment(2, 1000, 1900, "Tạm biệt")), srt);
        var output = tempDir.resolve("merged.mp4");
        merger.merge(fixture, List.of(new SubtitleTrack(srt, Language.VIETNAMESE)), output);

        var subtitleLanguage = probe(output, "-select_streams", "s",
                "-show_entries", "stream_tags=language", "-of", "csv=p=0");
        var videoCodec = probe(output, "-select_streams", "v",
                "-show_entries", "stream=codec_type", "-of", "csv=p=0");
        var audioCodec = probe(output, "-select_streams", "a",
                "-show_entries", "stream=codec_type", "-of", "csv=p=0");
        assertThat(subtitleLanguage).isEqualTo("vie");
        assertThat(videoCodec).isEqualTo("video");
        assertThat(audioCodec).isEqualTo("audio");
    }

    @Test
    void mergesMultipleSubtitleTracksWithLanguageTags() {
        Assumptions.assumeTrue(isRunnable("ffmpeg"), "ffmpeg is not available");
        Assumptions.assumeTrue(isRunnable("ffprobe"), "ffprobe is not available");
        var runner = new FfmpegRunner(new MediaProperties("ffmpeg", Duration.ofSeconds(60)));
        var writer = new SrtSubtitleWriter();
        var merger = new FfmpegSubtitleMerger(runner);
        var fixture = tempDir.resolve("fixture.mp4");
        runner.run(List.of(
                "-f", "lavfi", "-i", "testsrc=d=2:size=160x120:rate=10",
                "-f", "lavfi", "-i", "sine=d=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-shortest",
                fixture.toString()));
        var tracks = new ArrayList<SubtitleTrack>();
        for (var language : List.of(Language.ENGLISH, Language.VIETNAMESE, Language.JAPANESE)) {
            var srt = tempDir.resolve(language.getCode() + ".srt");
            writer.write(List.of(new SubtitleSegment(1, 0, 900, "Line " + language.getCode())), srt);
            tracks.add(new SubtitleTrack(srt, language));
        }
        var output = tempDir.resolve("multi.mp4");

        merger.merge(fixture, tracks, output);

        var subtitleLanguages = probe(output, "-select_streams", "s",
                "-show_entries", "stream_tags=language", "-of", "csv=p=0");
        var audioCodec = probe(output, "-select_streams", "a",
                "-show_entries", "stream=codec_type", "-of", "csv=p=0");
        assertThat(subtitleLanguages.lines().toList()).containsExactly("eng", "vie", "jpn");
        assertThat(audioCodec).isEqualTo("audio");
    }

    private static boolean isRunnable(String executable) {
        try {
            var process = new ProcessBuilder(executable, "-version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String probe(Path file, String... options) {
        var command = new ArrayList<String>();
        command.add("ffprobe");
        command.add("-v");
        command.add("error");
        command.addAll(List.of(options));
        command.add(file.toString());
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as(output).isZero();
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("ffprobe failed to run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ffprobe interrupted", e);
        }
    }
}
