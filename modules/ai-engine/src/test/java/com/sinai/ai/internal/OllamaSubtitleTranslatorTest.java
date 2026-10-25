package com.sinai.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sinai.ai.config.AiProperties;
import com.sinai.ai.internal.llm.OllamaSubtitleTranslator;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaSubtitleTranslatorTest {

    private static final String URL = "http://ollama/api/chat";

    private MockRestServiceServer server;
    private OllamaSubtitleTranslator translator;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://ollama");
        server = MockRestServiceServer.bindTo(builder).build();
        var ollama = new AiProperties.Ollama(null, null, Duration.ofSeconds(5), 2);
        translator = new OllamaSubtitleTranslator(builder.build(), new AiProperties(null, ollama));
    }

    private static List<SubtitleSegment> segments(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new SubtitleSegment(i, i * 1000L, i * 1000L + 500, "line " + i))
                .toList();
    }

    private static String reply(String translationsJson) {
        var content = "{\"translations\":" + translationsJson + "}";
        var escaped = content.replace("\"", "\\\"");
        return "{\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"},\"done\":true}";
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(translator.translate(List.of(), Language.ENGLISH, Language.VIETNAMESE)).isEmpty();
    }

    @Test
    void splitsIntoBatchesAndKeepsTimingWithBlankFallback() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                reply("[{\"index\":1,\"text\":\"dong 1\"},{\"index\":2,\"text\":\"dong 2\"}]"),
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                reply("[{\"index\":3,\"text\":\"  \"}]"), MediaType.APPLICATION_JSON));

        var result = translator.translate(segments(3), Language.ENGLISH, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("dong 1", "dong 2", "line 3");
        assertThat(result).extracting(SubtitleSegment::startTimeMs).containsExactly(1000L, 2000L, 3000L);
        assertThat(result).extracting(SubtitleSegment::endTimeMs).containsExactly(1500L, 2500L, 3500L);
        server.verify();
    }

    @Test
    void retriesOnceOnIndexMismatchThenSucceeds() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                reply("[{\"index\":1,\"text\":\"a\"}]"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                reply("[{\"index\":1,\"text\":\"a\"},{\"index\":2,\"text\":\"b\"}]"), MediaType.APPLICATION_JSON));

        var result = translator.translate(segments(2), Language.ENGLISH, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("a", "b");
        server.verify();
    }

    @Test
    void failsAfterSecondIndexMismatch() {
        server.expect(times(2), requestTo(URL)).andRespond(withSuccess(
                reply("[{\"index\":1,\"text\":\"a\"},{\"index\":9,\"text\":\"b\"}]"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> translator.translate(segments(2), Language.ENGLISH, Language.VIETNAMESE))
                .isInstanceOf(SinAiException.class)
                .extracting(e -> ((SinAiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TRANSLATION_ERROR);
        server.verify();
    }
}
