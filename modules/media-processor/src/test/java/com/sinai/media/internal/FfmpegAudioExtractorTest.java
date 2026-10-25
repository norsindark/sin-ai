package com.sinai.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FfmpegAudioExtractorTest {

    @Test
    void buildsExtractionArguments() {
        var arguments = FfmpegAudioExtractor.buildArguments(Path.of("in.mp4"), Path.of("out.wav"));

        assertThat(arguments).containsExactly(
                "-i", "in.mp4", "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "out.wav");
    }

    @Test
    void buildsFullCommandWithFfmpegPathFirst() {
        var arguments = FfmpegAudioExtractor.buildArguments(Path.of("in.mp4"), Path.of("out.wav"));

        var command = FfmpegRunner.buildCommand("/usr/bin/ffmpeg", arguments);

        assertThat(command).startsWith("/usr/bin/ffmpeg", "-hide_banner", "-nostdin", "-y", "-i", "in.mp4");
        assertThat(command).containsSequence("-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le");
        assertThat(command).endsWith("out.wav");
    }

    @Test
    void rejectsNullVideo() {
        assertThatThrownBy(() -> FfmpegAudioExtractor.validate(null, Path.of("out.wav")))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void rejectsMissingVideo() {
        var missing = Path.of("definitely-missing-video.mp4");

        assertThatThrownBy(() -> FfmpegAudioExtractor.validate(missing, Path.of("out.wav")))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }
}
