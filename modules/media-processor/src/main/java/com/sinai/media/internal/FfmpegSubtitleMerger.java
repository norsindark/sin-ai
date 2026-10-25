package com.sinai.media.internal;

import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.media.api.SubtitleMerger;
import com.sinai.media.api.SubtitleTrack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class FfmpegSubtitleMerger implements SubtitleMerger {

    private final FfmpegRunner runner;

    @Override
    public void merge(Path video, List<SubtitleTrack> tracks, Path outputVideo) {
        validate(video, tracks, outputVideo);
        OutputPaths.createParentDirectory(outputVideo);
        runner.run(buildArguments(video, tracks, outputVideo));
    }

    static List<String> buildArguments(Path video, List<SubtitleTrack> tracks, Path outputVideo) {
        var arguments = new ArrayList<String>();
        arguments.add("-i");
        arguments.add(video.toString());
        for (var track : tracks) {
            arguments.add("-i");
            arguments.add(track.srtFile().toString());
        }
        arguments.addAll(List.of("-map", "0:v", "-map", "0:a?"));
        for (var input = 1; input <= tracks.size(); input++) {
            arguments.add("-map");
            arguments.add(input + ":0");
        }
        arguments.addAll(List.of("-c", "copy", "-c:s", "mov_text"));
        for (var stream = 0; stream < tracks.size(); stream++) {
            arguments.add("-metadata:s:s:" + stream);
            arguments.add("language=" + toIso6392(tracks.get(stream).language()));
        }
        arguments.add(outputVideo.toString());
        return List.copyOf(arguments);
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

    static void validate(Path video, List<SubtitleTrack> tracks, Path outputVideo) {
        if (Objects.isNull(video) || !Files.isRegularFile(video)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Input video does not exist: " + video);
        }
        if (CollectionUtils.isEmpty(tracks)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "At least one subtitle track is required");
        }
        for (var track : tracks) {
            if (Objects.isNull(track)) {
                throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Subtitle track must not be null");
            }
            if (Objects.isNull(track.srtFile()) || !Files.isRegularFile(track.srtFile())) {
                throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "SRT file does not exist: " + track.srtFile());
            }
            if (Objects.isNull(track.language())) {
                throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Language must not be null");
            }
        }
        if (Objects.isNull(outputVideo)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Output video path must not be null");
        }
        if (video.toAbsolutePath().normalize().equals(outputVideo.toAbsolutePath().normalize())) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Output video must differ from input video");
        }
    }
}
