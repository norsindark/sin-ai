package com.sinai.media.internal;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.media.api.AudioExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FfmpegAudioExtractor implements AudioExtractor {

    private final FfmpegRunner runner;

    @Override
    public void extractAudio(Path video, Path outputWav) {
        validate(video, outputWav);
        OutputPaths.createParentDirectory(outputWav);
        runner.run(buildArguments(video, outputWav));
    }

    static List<String> buildArguments(Path video, Path outputWav) {
        return List.of(
                "-i", video.toString(),
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                "-c:a", "pcm_s16le",
                outputWav.toString());
    }

    static void validate(Path video, Path outputWav) {
        if (Objects.isNull(video) || !Files.isRegularFile(video)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Input video does not exist: " + video);
        }
        if (Objects.isNull(outputWav)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Output wav path must not be null");
        }
    }
}
