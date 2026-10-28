package com.sinai.ai.internal.llm;

import com.sinai.ai.api.ProgressListener;
import com.sinai.ai.api.SubtitleTranslator;
import com.sinai.ai.config.AiProperties;
import com.sinai.ai.internal.llm.TranslationValidator.TranslatedLine;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class OllamaSubtitleTranslator implements SubtitleTranslator {

    private static final String TRANSLATION_OBSERVATION = "sinai.translation";
    private static final String BATCH_OBSERVATION = "sinai.translation.batch";
    private static final String FALLBACK_OBSERVATION = "sinai.translation.fallback";
    private static final int MAX_TRANSPORT_ATTEMPTS = 2;
    private static final int CONTEXT_LINES = 3;
    private static final double TEMPERATURE = 0.2;
    private static final double STRICT_TEMPERATURE = 0.0;
    private static final int CONTEXT_WINDOW_TOKENS = 4096;
    private static final int BASE_OUTPUT_TOKENS = 64;
    private static final int OUTPUT_TOKENS_PER_LINE = 192;

    private static final double REPEAT_PENALTY = 1.15;
    private static final int MAX_LINE_CHARACTERS = 200;

    private static final Map<String, Object> RESPONSE_SCHEMA = ordered(
            "type", "object",
            "properties", ordered(
                    "translations", ordered(
                            "type", "array",
                            "items", ordered(
                                    "type", "object",
                                    "properties", ordered(
                                            "index", ordered("type", "integer"),
                                            "source", ordered("type", "string", "maxLength", MAX_LINE_CHARACTERS),
                                            "text", ordered("type", "string", "maxLength", MAX_LINE_CHARACTERS)),
                                    "required", List.of("index", "source", "text")))),
            "required", List.of("translations"));

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObservationRegistry observationRegistry;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OllamaSubtitleTranslator(@Qualifier("ollamaRestClient") RestClient restClient, AiProperties properties,
            ObservationRegistry observationRegistry) {
        this.restClient = restClient;
        this.properties = properties;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public List<SubtitleSegment> translate(List<SubtitleSegment> segments, Language source, Language target,
            ProgressListener progress) {
        if (CollectionUtils.isEmpty(segments)) {
            return Collections.emptyList();
        }
        if (Objects.isNull(source) || Objects.isNull(target)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "Source and target languages are required");
        }
        var listener = Objects.requireNonNullElse(progress, ProgressListener.NONE);
        return Observation.createNotStarted(TRANSLATION_OBSERVATION, observationRegistry)
                .contextualName("translate " + source.getCode() + " to " + target.getCode())
                .lowCardinalityKeyValue("source.language", source.getCode())
                .lowCardinalityKeyValue("target.language", target.getCode())
                .lowCardinalityKeyValue("model", properties.ollama().model())
                .highCardinalityKeyValue("lines", String.valueOf(segments.size()))
                .observe(() -> translateAll(segments, source, target, listener));
    }

    private List<SubtitleSegment> translateAll(List<SubtitleSegment> segments, Language source, Language target,
            ProgressListener listener) {
        var batchSize = properties.ollama().batchSize();
        var result = new ArrayList<SubtitleSegment>(segments.size());
        listener.onProgress(0);
        for (var from = 0; from < segments.size(); from += batchSize) {
            var to = Math.min(from + batchSize, segments.size());
            result.addAll(translateRange(segments, from, to, source, target));
            listener.onProgress(to * 100 / segments.size());
        }
        return result;
    }

    private List<SubtitleSegment> translateRange(List<SubtitleSegment> segments, int from, int to, Language source,
            Language target) {
        var batch = segments.subList(from, to);
        var contextBefore = texts(segments.subList(Math.max(0, from - CONTEXT_LINES), from));
        var contextAfter = texts(segments.subList(to, Math.min(segments.size(), to + CONTEXT_LINES)));
        var attempt = attempt(batch, contextBefore, contextAfter, source, target, List.of());
        if (attempt.problems().isEmpty()) {
            return merge(batch, attempt.translated());
        }
        log.warn("Translation of lines {}-{} to {} rejected: {}", batch.getFirst().index(), batch.getLast().index(),
                target.getCode(), attempt.problems());
        attempt = attempt(batch, contextBefore, contextAfter, source, target, attempt.problems());
        if (attempt.problems().isEmpty()) {
            return merge(batch, attempt.translated());
        }
        log.warn("Strict retry of lines {}-{} to {} rejected: {}", batch.getFirst().index(),
                batch.getLast().index(), target.getCode(), attempt.problems());
        if (batch.size() > 1) {
            var middle = from + batch.size() / 2;
            var result = new ArrayList<>(translateRange(segments, from, middle, source, target));
            result.addAll(translateRange(segments, middle, to, source, target));
            return result;
        }
        var original = batch.getFirst();
        return List.of(fallback(original, attempt.translated().get(original.index()), source, target));
    }

    private Attempt attempt(List<SubtitleSegment> batch, List<String> contextBefore, List<String> contextAfter,
            Language source, Language target, List<String> previousProblems) {
        var observation = Observation.createNotStarted(BATCH_OBSERVATION, observationRegistry)
                .contextualName("translate batch")
                .lowCardinalityKeyValue("target.language", target.getCode())
                .lowCardinalityKeyValue("attempt", previousProblems.isEmpty() ? "initial" : "strict")
                .highCardinalityKeyValue("lines", batch.getFirst().index() + "-" + batch.getLast().index())
                .start();
        try (var scope = observation.openScope()) {
            var reply = requestTranslations(batch, contextBefore, contextAfter, source, target, previousProblems);
            var problems = TranslationValidator.findProblems(batch, reply.translated(), target);
            observation.lowCardinalityKeyValue("outcome", problems.isEmpty() ? "accepted" : "rejected")
                    .highCardinalityKeyValue("ollama.prompt.tokens", String.valueOf(reply.promptTokens()))
                    .highCardinalityKeyValue("ollama.output.tokens", String.valueOf(reply.outputTokens()));
            if (!problems.isEmpty()) {
                observation.highCardinalityKeyValue("problems", String.join("; ", problems));
            }
            return new Attempt(reply.translated(), problems);
        } catch (RuntimeException e) {
            observation.lowCardinalityKeyValue("outcome", "error").error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private SubtitleSegment fallback(SubtitleSegment original, TranslatedLine line, Language source,
            Language target) {
        var text = Objects.isNull(line) ? StringUtils.EMPTY : ScriptPolicy.stripForeignScript(line.text(), target);
        var kind = "cleaned";
        if (TranslationValidator.normalize(text).isEmpty()) {
            log.warn("Line {} kept untranslated after failed retries (source {})", original.index(),
                    source.getCode());
            text = original.text();
            kind = "untranslated";
        } else {
            log.warn("Line {} uses a cleaned translation after failed retries", original.index());
        }
        Observation.createNotStarted(FALLBACK_OBSERVATION, observationRegistry)
                .lowCardinalityKeyValue("target.language", target.getCode())
                .lowCardinalityKeyValue("kind", kind)
                .highCardinalityKeyValue("line", String.valueOf(original.index()))
                .observe(() -> {
                });
        return new SubtitleSegment(original.index(), original.startTimeMs(), original.endTimeMs(), text);
    }

    private OllamaReply requestTranslations(List<SubtitleSegment> batch, List<String> contextBefore,
            List<String> contextAfter, Language source, Language target, List<String> previousProblems) {
        var request = buildRequest(batch, contextBefore, contextAfter, source, target, previousProblems);
        if (log.isDebugEnabled()) {
            log.debug("Ollama request: {}", jsonMapper.writeValueAsString(request));
        }
        SinAiException lastFailure = null;
        for (var attempt = 1; attempt <= MAX_TRANSPORT_ATTEMPTS; attempt++) {
            try {
                var response = restClient.post()
                        .uri("/api/chat")
                        .body(request)
                        .retrieve()
                        .body(String.class);
                log.debug("Ollama response: {}", response);
                return parse(response);
            } catch (RestClientException e) {
                lastFailure = new SinAiException(ErrorCode.TRANSLATION_ERROR,
                        "Translation failed: " + e.getMessage(), e);
            }
        }
        throw lastFailure;
    }

    private OllamaReply parse(String response) {
        var translated = new HashMap<Integer, TranslatedLine>();
        var promptTokens = 0L;
        var outputTokens = 0L;
        try {
            var root = jsonMapper.readTree(response);
            promptTokens = root.path("prompt_eval_count").asLong(0);
            outputTokens = root.path("eval_count").asLong(0);
            var content = root.path("message").path("content").asString("");
            var parsed = jsonMapper.readValue(content, TranslationPayload.class);
            if (Objects.nonNull(parsed) && Objects.nonNull(parsed.translations())) {
                for (var item : parsed.translations()) {
                    translated.putIfAbsent(item.index(), item);
                }
            }
        } catch (JacksonException e) {
            log.warn("Cannot parse translation response: {}", e.getMessage());
        }
        return new OllamaReply(translated, promptTokens, outputTokens);
    }

    private List<SubtitleSegment> merge(List<SubtitleSegment> batch, Map<Integer, TranslatedLine> translated) {
        var merged = new ArrayList<SubtitleSegment>(batch.size());
        for (var original : batch) {
            var text = StringUtils.normalizeSpace(translated.get(original.index()).text());
            merged.add(new SubtitleSegment(original.index(), original.startTimeMs(), original.endTimeMs(), text));
        }
        return merged;
    }

    private Map<String, Object> buildRequest(List<SubtitleSegment> batch, List<String> contextBefore,
            List<String> contextAfter, Language source, Language target, List<String> previousProblems) {
        var lines = batch.stream()
                .map(segment -> ordered("index", segment.index(), "source", segment.text()))
                .toList();
        var payload = ordered(
                "context_before", contextBefore,
                "lines", lines,
                "context_after", contextAfter);
        var temperature = CollectionUtils.isEmpty(previousProblems) ? TEMPERATURE : STRICT_TEMPERATURE;
        return ordered(
                "model", properties.ollama().model(),
                "stream", false,
                "format", RESPONSE_SCHEMA,
                "options", ordered(
                        "temperature", temperature,
                        "repeat_penalty", REPEAT_PENALTY,
                        "num_ctx", CONTEXT_WINDOW_TOKENS,
                        "num_predict", BASE_OUTPUT_TOKENS + OUTPUT_TOKENS_PER_LINE * batch.size()),
                "messages", List.of(
                        ordered("role", "system", "content",
                                TranslationPrompt.system(source, target, previousProblems)),
                        ordered("role", "user", "content", jsonMapper.writeValueAsString(payload))));
    }

    private static Map<String, Object> ordered(Object... keysAndValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var position = 0; position < keysAndValues.length; position += 2) {
            map.put((String) keysAndValues[position], keysAndValues[position + 1]);
        }
        return map;
    }

    private static List<String> texts(List<SubtitleSegment> segments) {
        return segments.stream().map(SubtitleSegment::text).toList();
    }

    record TranslationPayload(List<TranslatedLine> translations) {
    }

    private record OllamaReply(Map<Integer, TranslatedLine> translated, long promptTokens, long outputTokens) {
    }

    private record Attempt(Map<Integer, TranslatedLine> translated, List<String> problems) {
    }
}
