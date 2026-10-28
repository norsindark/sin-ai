package com.sinai.ai.internal.whisper;

import com.sinai.ai.api.ProgressListener;
import com.sinai.ai.api.SpeechTranscriber;
import com.sinai.ai.config.AiProperties;
import com.sinai.ai.internal.whisper.CueSegmenter.Cue;
import com.sinai.ai.internal.whisper.CueSegmenter.TimedWord;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class WhisperTranscriber implements SpeechTranscriber {

    private static final String TIMESTAMP_GRANULARITIES = "timestamp_granularities[]";
    private static final String TRANSCRIPTION_OBSERVATION = "sinai.transcription";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";
    private static final int MAX_STREAMING_PERCENT = 99;

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObservationRegistry observationRegistry;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public WhisperTranscriber(@Qualifier("whisperRestClient") RestClient restClient, AiProperties properties,
            ObservationRegistry observationRegistry) {
        this.restClient = restClient;
        this.properties = properties;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public List<SubtitleSegment> transcribe(Path wav, Language language, ProgressListener progress) {
        if (Objects.isNull(wav) || !Files.isRegularFile(wav)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Input wav does not exist: " + wav);
        }
        var listener = Objects.requireNonNullElse(progress, ProgressListener.NONE);
        var durationSeconds = audioDurationSeconds(wav);
        var observation = Observation.createNotStarted(TRANSCRIPTION_OBSERVATION, observationRegistry)
                .contextualName("transcribe")
                .lowCardinalityKeyValue("language", Objects.isNull(language) ? "auto" : language.getCode())
                .lowCardinalityKeyValue("model", properties.whisper().model())
                .lowCardinalityKeyValue("vad", String.valueOf(properties.whisper().vadFilter()))
                .highCardinalityKeyValue("audio.seconds", String.valueOf(Math.round(durationSeconds)));
        return observation.observe(() -> {
            var segments = stream(wav, language, durationSeconds, listener);
            observation.highCardinalityKeyValue("cues", String.valueOf(segments.size()));
            return segments;
        });
    }

    private List<SubtitleSegment> stream(Path wav, Language language, double durationSeconds,
            ProgressListener listener) {
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new FileSystemResource(wav));
        form.add("model", properties.whisper().model());
        form.add("response_format", "verbose_json");
        form.add("stream", "true");
        form.add("vad_filter", String.valueOf(properties.whisper().vadFilter()));
        form.add(TIMESTAMP_GRANULARITIES, "segment");
        form.add(TIMESTAMP_GRANULARITIES, "word");
        if (Objects.nonNull(language)) {
            form.add("language", language.getCode());
        }
        listener.onProgress(0);
        try {
            var segments = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(form)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new SinAiException(ErrorCode.TRANSCRIPTION_ERROR,
                                    "Transcription failed with status " + response.getStatusCode().value());
                        }
                        return readEvents(response.getBody(), durationSeconds, listener);
                    });
            listener.onProgress(100);
            if (language == Language.CHINESE) {
                segments = segments.stream().map(WhisperTranscriber::simplifyChinese).toList();
            }
            return toSegments(new WhisperResponse(segments));
        } catch (RestClientException | JacksonException e) {
            throw new SinAiException(ErrorCode.TRANSCRIPTION_ERROR, "Transcription failed: " + e.getMessage(), e);
        }
    }

    private List<RawSegment> readEvents(InputStream body, double durationSeconds, ProgressListener listener)
            throws IOException {
        var segments = new ArrayList<RawSegment>();
        var lastPercent = 0;
        try (var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            var line = reader.readLine();
            while (Objects.nonNull(line)) {
                var data = line.startsWith(SSE_DATA_PREFIX)
                        ? StringUtils.trim(line.substring(SSE_DATA_PREFIX.length()))
                        : StringUtils.EMPTY;
                if (StringUtils.isNotBlank(data) && !SSE_DONE.equals(data)) {
                    var event = jsonMapper.readValue(data, WhisperResponse.class);
                    if (Objects.nonNull(event) && !CollectionUtils.isEmpty(event.segments())) {
                        segments.addAll(event.segments());
                        var percent = percentOf(event.segments().getLast().end(), durationSeconds);
                        if (percent > lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                    }
                }
                line = reader.readLine();
            }
        }
        return segments;
    }

    static int percentOf(double positionSeconds, double durationSeconds) {
        if (durationSeconds <= 0) {
            return 0;
        }
        return (int) Math.min(MAX_STREAMING_PERCENT, Math.max(0, Math.floor(positionSeconds * 100 / durationSeconds)));
    }

    static double audioDurationSeconds(Path wav) {
        try {
            var format = AudioSystem.getAudioFileFormat(wav.toFile());
            if (format.getFrameLength() > 0 && format.getFormat().getFrameRate() > 0) {
                return format.getFrameLength() / (double) format.getFormat().getFrameRate();
            }
        } catch (UnsupportedAudioFileException | IOException e) {
            log.debug("Cannot read audio header of {}: {}", wav, e.getMessage());
        }
        return 0;
    }

    static List<SubtitleSegment> toSegments(WhisperResponse response) {
        if (Objects.isNull(response) || CollectionUtils.isEmpty(response.segments())) {
            return Collections.emptyList();
        }
        var result = new ArrayList<SubtitleSegment>();
        for (var raw : response.segments()) {
            for (var cue : toCues(raw)) {
                if (HallucinationFilter.isHallucination(cue.text())) {
                    log.debug("Dropping likely hallucinated cue at {} ms: {}", cue.startMs(), cue.text());
                    continue;
                }
                result.add(new SubtitleSegment(result.size() + 1, cue.startMs(), cue.endMs(), cue.text()));
            }
        }
        return result;
    }

    private static List<Cue> toCues(RawSegment raw) {
        var words = toTimedWords(raw.words());
        if (!CollectionUtils.isEmpty(words)) {
            return CueSegmenter.split(words);
        }
        var text = CueSegmenter.normalize(raw.text());
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        var startMs = toMillis(raw.start());
        var endMs = toMillis(raw.end());
        if (endMs <= startMs) {
            endMs = startMs + 1;
        }
        return List.of(new Cue(startMs, endMs, text));
    }

    private static List<TimedWord> toTimedWords(List<RawWord> words) {
        if (CollectionUtils.isEmpty(words)) {
            return Collections.emptyList();
        }
        return words.stream()
                .filter(word -> StringUtils.isNotBlank(word.word()))
                .map(word -> new TimedWord(toMillis(word.start()), toMillis(word.end()), word.word()))
                .toList();
    }

    private static RawSegment simplifyChinese(RawSegment raw) {
        var words = CollectionUtils.isEmpty(raw.words())
                ? raw.words()
                : raw.words().stream()
                        .map(word -> new RawWord(word.start(), word.end(), ChineseScript.toSimplified(word.word())))
                        .toList();
        return new RawSegment(raw.start(), raw.end(), ChineseScript.toSimplified(raw.text()), words);
    }

    private static long toMillis(double seconds) {
        return Math.max(0L, Math.round(seconds * 1000));
    }

    record WhisperResponse(List<RawSegment> segments) {
    }

    record RawSegment(double start, double end, String text, List<RawWord> words) {
    }

    record RawWord(double start, double end, String word) {
    }
}
