package com.sinai.ai.internal.llm;

import com.sinai.ai.api.SubtitleTranslator;
import com.sinai.ai.config.AiProperties;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OllamaSubtitleTranslator implements SubtitleTranslator {

    private static final int MAX_ATTEMPTS = 2;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "translations", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "index", Map.of("type", "integer"),
                                            "text", Map.of("type", "string")),
                                    "required", List.of("index", "text")))),
            "required", List.of("translations"));

    private final RestClient restClient;
    private final AiProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OllamaSubtitleTranslator(@Qualifier("ollamaRestClient") RestClient restClient, AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<SubtitleSegment> translate(List<SubtitleSegment> segments, Language source, Language target) {
        if (CollectionUtils.isEmpty(segments)) {
            return Collections.emptyList();
        }
        if (Objects.isNull(source) || Objects.isNull(target)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Source and target languages are required");
        }
        var batchSize = properties.ollama().batchSize();
        var result = new ArrayList<SubtitleSegment>(segments.size());
        for (var from = 0; from < segments.size(); from += batchSize) {
            var batch = segments.subList(from, Math.min(from + batchSize, segments.size()));
            result.addAll(translateBatch(batch, source, target));
        }
        return result;
    }

    private List<SubtitleSegment> translateBatch(List<SubtitleSegment> batch, Language source, Language target) {
        var request = buildRequest(batch, source, target);
        SinAiException lastFailure = null;
        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                var translated = requestTranslations(request);
                return merge(batch, translated);
            } catch (SinAiException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private Map<Integer, String> requestTranslations(Map<String, Object> request) {
        try {
            var response = restClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(String.class);
            var content = jsonMapper.readTree(response).path("message").path("content").asString("");
            var parsed = jsonMapper.readValue(content, TranslationPayload.class);
            var translated = new HashMap<Integer, String>();
            if (Objects.nonNull(parsed) && Objects.nonNull(parsed.translations())) {
                for (var item : parsed.translations()) {
                    translated.put(item.index(), item.text());
                }
            }
            return translated;
        } catch (RestClientException | JacksonException e) {
            throw new SinAiException(ErrorCode.TRANSLATION_ERROR, "Translation failed: " + e.getMessage(), e);
        }
    }

    private List<SubtitleSegment> merge(List<SubtitleSegment> batch, Map<Integer, String> translated) {
        var expected = batch.stream().map(SubtitleSegment::index).collect(Collectors.toSet());
        if (batch.size() != translated.size() || !expected.equals(translated.keySet())) {
            throw new SinAiException(ErrorCode.TRANSLATION_ERROR, "Translation indices do not match the request");
        }
        var merged = new ArrayList<SubtitleSegment>(batch.size());
        for (var original : batch) {
            var text = StringUtils.trim(translated.get(original.index()));
            var finalText = StringUtils.isBlank(text) ? original.text() : text;
            merged.add(new SubtitleSegment(original.index(), original.startTimeMs(), original.endTimeMs(), finalText));
        }
        return merged;
    }

    private Map<String, Object> buildRequest(List<SubtitleSegment> batch, Language source, Language target) {
        var lines = batch.stream()
                .map(segment -> Map.of("index", segment.index(), "text", segment.text()))
                .toList();
        var system = "You are a professional subtitle translator. Translate each subtitle line from "
                + source.getDisplayName() + " to " + target.getDisplayName() + ". "
                + "Keep the meaning, tone and brevity. Translate one-to-one: never merge, split, skip or add lines. "
                + "Return every input index exactly once with its translated text.";
        var user = jsonMapper.writeValueAsString(Map.of("lines", lines));
        return Map.of(
                "model", properties.ollama().model(),
                "stream", false,
                "format", RESPONSE_SCHEMA,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
    }

    record TranslationPayload(List<TranslatedLine> translations) {
    }

    record TranslatedLine(int index, String text) {
    }
}
