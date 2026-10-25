package com.sinai.media.internal;

import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.media.api.SubtitleMerger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FfmpegSubtitleMerger implements SubtitleMerger {

    private final FfmpegRunner runner;

    @Override
    public void merge(Path video, Path srtFile, Language language, Path outputVideo) {
        validate(video, srtFile, language, outputVideo);
        OutputPaths.createParentDirectory(outputVideo);
        runner.run(buildArguments(video, srtFile, language, outputVideo));
    }

    static List<String> buildArguments(Path video, Path srtFile, Language language, Path outputVideo) {
        return List.of(
                "-i", video.toString(),
                "-i", srtFile.toString(),
                "-map", "0:v",
                "-map", "0:a?",
                "-map", "1:0",
                "-c", "copy",
                "-c:s", "mov_text",
                "-metadata:s:s:0", "language=" + toIso6392(language),
                outputVideo.toString());
    }

    static String toIso6392(Language language) {
        return switch (language) {
            case ENGLISH -> "eng";
            case VIETNAMESE -> "vie";
            case JAPANESE -> "jpn";
            case KOREAN -> "kor";
            case CHINESE -> "zho";
            case FRENCH -> "fra";
            case GERMAN -> "deu";
            case SPANISH -> "spa";
        };
    }

    static void validate(Path video, Path srtFile, Language language, Path outputVideo) {
        if (Objects.isNull(video) || !Files.isRegularFile(video)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Input video does not exist: " + video);
        }
        if (Objects.isNull(srtFile) || !Files.isRegularFile(srtFile)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "SRT file does not exist: " + srtFile);
        }
        if (Objects.isNull(language)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Language must not be null");
        }
        if (Objects.isNull(outputVideo)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Output video path must not be null");
        }
        if (video.toAbsolutePath().normalize().equals(outputVideo.toAbsolutePath().normalize())) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Output video must differ from input video");
        }
    }
}
