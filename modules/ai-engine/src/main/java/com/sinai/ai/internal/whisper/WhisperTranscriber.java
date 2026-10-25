package com.sinai.ai.internal.whisper;

import com.sinai.ai.api.SpeechTranscriber;
import com.sinai.ai.config.AiProperties;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class WhisperTranscriber implements SpeechTranscriber {

    private final RestClient restClient;
    private final AiProperties properties;

    public WhisperTranscriber(@Qualifier("whisperRestClient") RestClient restClient, AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<SubtitleSegment> transcribe(Path wav, Language language) {
        if (Objects.isNull(wav) || !Files.isRegularFile(wav)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Input wav does not exist: " + wav);
        }
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new FileSystemResource(wav));
        form.add("model", properties.whisper().model());
        form.add("response_format", "verbose_json");
        if (Objects.nonNull(language)) {
            form.add("language", language.getCode());
        }
        try {
            var response = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(WhisperResponse.class);
            return toSegments(response);
        } catch (RestClientException e) {
            throw new SinAiException(ErrorCode.TRANSCRIPTION_ERROR, "Transcription failed: " + e.getMessage(), e);
        }
    }

    static List<SubtitleSegment> toSegments(WhisperResponse response) {
        if (Objects.isNull(response) || CollectionUtils.isEmpty(response.segments())) {
            return Collections.emptyList();
        }
        var result = new ArrayList<SubtitleSegment>();
        for (var raw : response.segments()) {
            var text = StringUtils.trim(raw.text());
            if (StringUtils.isBlank(text)) {
                continue;
            }
            var startMs = Math.max(0L, Math.round(raw.start() * 1000));
            var endMs = Math.round(raw.end() * 1000);
            if (endMs <= startMs) {
                endMs = startMs + 1;
            }
            result.add(new SubtitleSegment(result.size() + 1, startMs, endMs, text));
        }
        return result;
    }

    record WhisperResponse(List<RawSegment> segments) {
    }

    record RawSegment(double start, double end, String text) {
    }
}
