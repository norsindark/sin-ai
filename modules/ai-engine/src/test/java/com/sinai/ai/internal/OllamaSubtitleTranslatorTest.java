package com.sinai.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sinai.ai.config.AiProperties;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import com.sinai.ai.internal.llm.OllamaSubtitleTranslator;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OllamaSubtitleTranslatorTest {

    private static final String URL = "http://ollama/api/chat";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> SOURCES = List.of(
            "good morning everyone", "the river is cold", "we walk home together");

    private MockRestServiceServer server;
    private OllamaSubtitleTranslator translator;
    private final TestObservationRegistry observations = TestObservationRegistry.create();

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://ollama");
        server = MockRestServiceServer.bindTo(builder).build();
        var ollama = new AiProperties.Ollama(null, null, Duration.ofSeconds(5), 2);
        translator = new OllamaSubtitleTranslator(builder.build(), new AiProperties(null, ollama), observations);
    }

    private static List<SubtitleSegment> segments(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new SubtitleSegment(i, i * 1000L, i * 1000L + 500, SOURCES.get(i - 1)))
                .toList();
    }

    private static String reply(Object... indexSourceText) {
        var translations = new ArrayList<Map<String, Object>>();
        for (var position = 0; position < indexSourceText.length; position += 3) {
            translations.add(Map.of(
                    "index", indexSourceText[position],
                    "source", indexSourceText[position + 1],
                    "text", indexSourceText[position + 2]));
        }
        var content = JSON.writeValueAsString(Map.of("translations", translations));
        return JSON.writeValueAsString(Map.of(
                "message", Map.of("role", "assistant", "content", content),
                "done", true));
    }

    private void expectReply(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(translator.translate(List.of(), Language.ENGLISH, Language.VIETNAMESE)).isEmpty();
    }

    @Test
    void sendsOptionsAndReadOnlyContextAndKeepsTiming() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.options.temperature").value(0.2))
                .andExpect(jsonPath("$.options.num_ctx").value(4096))
                .andExpect(jsonPath("$.options.num_predict").value(448))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"context_after\":[\"we walk home together\"]")))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"context_before\":[]")))
                .andRespond(withSuccess(reply(1, "good morning everyone", "dòng một", 2, "the river is cold", "dòng hai"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[1].content",
                        containsString("\"context_before\":[\"good morning everyone\",\"the river is cold\"]")))
                .andRespond(withSuccess(reply(3, "we walk home together", "dòng ba"), MediaType.APPLICATION_JSON));

        var reported = new ArrayList<Integer>();

        var result = translator.translate(segments(3), Language.ENGLISH, Language.VIETNAMESE, reported::add);

        assertThat(reported).containsExactly(0, 66, 100);
        assertThat(result).extracting(SubtitleSegment::text).containsExactly("dòng một", "dòng hai", "dòng ba");
        assertThat(result).extracting(SubtitleSegment::startTimeMs).containsExactly(1000L, 2000L, 3000L);
        assertThat(result).extracting(SubtitleSegment::endTimeMs).containsExactly(1500L, 2500L, 3500L);
        server.verify();
    }

    @Test
    void ignoresEchoedContextLinesAndRetriesStrictlyWhenALineIsMissing() {
        expectReply(reply(1, "good morning everyone", "dòng một", 3, "we walk home together", "dòng ba"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.options.temperature").value(0.0))
                .andExpect(jsonPath("$.messages[0].content", containsString("line 2 is missing")))
                .andRespond(withSuccess(reply(1, "good morning everyone", "dòng một", 2, "the river is cold", "dòng hai", 3, "we walk home together", "x"),
                        MediaType.APPLICATION_JSON));

        var result = translator.translate(segments(2), Language.ENGLISH, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("dòng một", "dòng hai");
        server.verify();
    }

    @Test
    void splitsBatchIntoSingleLinesWhenTranslationsAreShifted() {
        var shifted = reply(1, "the river is cold", "dòng hai", 2, "we walk home together", "dòng ba");
        expectReply(shifted);
        expectReply(shifted);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"context_after\":[\"the river is cold\"]")))
                .andRespond(withSuccess(reply(1, "good morning everyone", "dòng một"), MediaType.APPLICATION_JSON));
        expectReply(reply(2, "the river is cold", "dòng hai"));

        var result = translator.translate(segments(2), Language.ENGLISH, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("dòng một", "dòng hai");
        server.verify();
    }

    @Test
    void rejectsRepeatedTranslationsOfDifferentLines() {
        var repeated = reply(1, "good morning everyone", "tôi chỉ muốn rời đi", 2, "the river is cold", "tôi chỉ muốn rời đi");
        expectReply(repeated);
        expectReply(repeated);
        expectReply(reply(1, "good morning everyone", "dòng một"));
        expectReply(reply(2, "the river is cold", "dòng hai"));

        var result = translator.translate(segments(2), Language.ENGLISH, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("dòng một", "dòng hai");
        server.verify();
    }

    @Test
    void stripsChineseCharactersWhenRetriesKeepLeakingThem() {
        var source = List.of(new SubtitleSegment(1, 0, 1000, "我曾经跨过山河大海"));
        expectReply(reply(1, "我曾经跨过山河大海", "Tôi từng vượt qua bao山水大海"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[0].content",
                        containsString("line 1 contains characters outside Vietnamese")))
                .andRespond(withSuccess(reply(1, "我曾经跨过山河大海", "Tôi từng vượt qua bao 山水大海"),
                        MediaType.APPLICATION_JSON));

        var result = translator.translate(source, Language.CHINESE, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("Tôi từng vượt qua bao");
        server.verify();
        TestObservationRegistryAssert.assertThat(observations)
                .hasNumberOfObservationsWithNameEqualTo("sinai.translation.batch", 2)
                .hasObservationWithNameEqualTo("sinai.translation.fallback").that()
                .hasLowCardinalityKeyValue("kind", "cleaned")
                .hasLowCardinalityKeyValue("target.language", "vi");
        TestObservationRegistryAssert.assertThat(observations)
                .hasObservationWithNameEqualTo("sinai.translation.batch").that()
                .hasLowCardinalityKeyValue("outcome", "rejected");
    }

    @Test
    void retriesWhenVietnameseTargetComesBackInEnglish() {
        var source = List.of(new SubtitleSegment(1, 0, 1000, "借苦借相瞒着"));
        expectReply(reply(1, "借苦借相瞒着", "Borrowing sorrow, concealing it"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[0].content", containsString("is not written in Vietnamese")))
                .andRespond(withSuccess(reply(1, "借苦借相瞒着", "Mượn nỗi đau để che giấu"),
                        MediaType.APPLICATION_JSON));

        var result = translator.translate(source, Language.CHINESE, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("Mượn nỗi đau để che giấu");
        server.verify();
    }

    @Test
    void keepsSourceTextWhenSingleLineStaysEmpty() {
        var source = List.of(new SubtitleSegment(1, 0, 1000, "Hello there"));
        expectReply(reply(1, "Hello there", "  "));
        expectReply(reply(1, "Hello there", "你好"));

        var result = translator.translate(source, Language.ENGLISH, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("Hello there");
        server.verify();
    }

    @Test
    void retriesTruncatedAndPunctuationOnlyTranslations() {
        var source = List.of(
                new SubtitleSegment(1, 0, 1000, "也穿过人生人海"),
                new SubtitleSegment(2, 1000, 2000, "我曾经拥有着的一切"));
        expectReply(reply(1, "也穿过人生人海", "đã", 2, "我曾经拥有着的一切", "。"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[0].content", containsString("line 1 is cut short")))
                .andExpect(jsonPath("$.messages[0].content", containsString("line 2 has no words")))
                .andRespond(withSuccess(reply(1, "也穿过人生人海", "Cũng đi qua biển người mênh mông",
                        2, "我曾经拥有着的一切", "Tôi từng có tất cả mọi thứ"), MediaType.APPLICATION_JSON));

        var result = translator.translate(source, Language.CHINESE, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text)
                .containsExactly("Cũng đi qua biển người mênh mông", "Tôi từng có tất cả mọi thứ");
        server.verify();
    }

    @Test
    void keepsSourceTextWhenSingleLineStaysPunctuationOnly() {
        var source = List.of(new SubtitleSegment(1, 0, 1000, "Hello there"));
        expectReply(reply(1, "Hello there", "。"));
        expectReply(reply(1, "Hello there", "..."));

        var result = translator.translate(source, Language.ENGLISH, Language.VIETNAMESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("Hello there");
        server.verify();
    }

    @Test
    void allowsChineseCharactersForChineseTarget() {
        var source = List.of(new SubtitleSegment(1, 0, 1000, "Hello"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[0].content", not(containsString("rejected"))))
                .andExpect(jsonPath("$.messages[1].content", containsString("\"lines\"")))
                .andRespond(withSuccess(reply(1, "Hello", "你好"), MediaType.APPLICATION_JSON));

        var result = translator.translate(source, Language.ENGLISH, Language.CHINESE);

        assertThat(result).extracting(SubtitleSegment::text).containsExactly("你好");
        server.verify();
    }

    @Test
    void failsAfterRepeatedServerErrors() {
        server.expect(times(2), requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> translator.translate(segments(2), Language.ENGLISH, Language.VIETNAMESE))
                .isInstanceOf(SinAiException.class)
                .extracting(e -> ((SinAiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TRANSLATION_ERROR);
        server.verify();
    }

    @Test
    void sendsBatchLinesWithIndexAndSource() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.format.properties.translations.items.required",
                        contains("index", "source", "text")))
                .andExpect(content().string(containsString(
                        "\"properties\":{\"index\":{\"type\":\"integer\"},"
                                + "\"source\":{\"type\":\"string\",\"maxLength\":200}")))
                .andExpect(jsonPath("$.messages[1].content", containsString(
                        "{\"context_before\":[],\"lines\":[{\"index\":1,\"source\":\"good morning everyone\"}]")))
                .andExpect(jsonPath("$.options.repeat_penalty").value(1.15))
                .andRespond(withSuccess(reply(1, "good morning everyone", "dòng một"), MediaType.APPLICATION_JSON));

        translator.translate(segments(1), Language.ENGLISH, Language.VIETNAMESE);

        server.verify();
    }
}
