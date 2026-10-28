package com.sinai.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sinai.ai.config.AiProperties;
import io.micrometer.observation.tck.TestObservationRegistry;
import com.sinai.ai.internal.whisper.WhisperTranscriber;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WhisperTranscriberTest {

    private static final String URL = "http://whisper/v1/audio/transcriptions";
    private static final int SAMPLE_RATE = 16000;

    @TempDir
    Path tempDir;

    private MockRestServiceServer server;
    private WhisperTranscriber transcriber;
    private final TestObservationRegistry observations = TestObservationRegistry.create();
    private Path wav;

    @BeforeEach
    void setUp() throws Exception {
        var builder = RestClient.builder().baseUrl("http://whisper");
        server = MockRestServiceServer.bindTo(builder).build();
        transcriber = new WhisperTranscriber(builder.build(), new AiProperties(null, null), observations);
        wav = writeSilentWav(tempDir.resolve("a.wav"), 10);
    }

    private static Path writeSilentWav(Path target, int seconds) throws Exception {
        var format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        var frames = (long) SAMPLE_RATE * seconds;
        try (var stream = new AudioInputStream(new ByteArrayInputStream(new byte[(int) frames * 2]), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, target.toFile());
        }
        return target;
    }

    private static String sse(String... events) {
        var builder = new StringBuilder();
        for (var event : events) {
            builder.append("data: ").append(event).append("\n\n");
        }
        return builder.toString();
    }

    private static String event(String segmentsJson) {
        return "{\"task\":\"transcribe\",\"language\":\"en\",\"text\":\"x\",\"segments\":[" + segmentsJson + "]}";
    }

    @Test
    void mapsFiltersAndRenumbersStreamedSegments() {
        var body = sse(
                event("{\"id\":0,\"start\":0.0,\"end\":1.2344,\"text\":\"  Hello  \"}"),
                event("{\"id\":1,\"start\":1.3,\"end\":2.0,\"text\":\"   \"}"),
                event("{\"id\":2,\"start\":3.0004,\"end\":3.0001,\"text\":\"Zero length\"}"),
                event("{\"id\":3,\"start\":4.5,\"end\":6.0,\"text\":\"World\"}"));
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.TEXT_EVENT_STREAM));

        var segments = transcriber.transcribe(wav, Language.ENGLISH);

        assertThat(segments).hasSize(3);
        assertThat(segments.get(0).index()).isEqualTo(1);
        assertThat(segments.get(0).startTimeMs()).isZero();
        assertThat(segments.get(0).endTimeMs()).isEqualTo(1234);
        assertThat(segments.get(0).text()).isEqualTo("Hello");
        assertThat(segments.get(1).index()).isEqualTo(2);
        assertThat(segments.get(1).startTimeMs()).isEqualTo(3000);
        assertThat(segments.get(1).endTimeMs()).isEqualTo(3001);
        assertThat(segments.get(2).index()).isEqualTo(3);
        assertThat(segments.get(2).text()).isEqualTo("World");
        server.verify();
    }

    @Test
    void reportsProgressFromStreamedSegmentEnds() {
        var body = sse(
                event("{\"id\":1,\"start\":0.0,\"end\":2.5,\"text\":\"one\"}"),
                event("{\"id\":2,\"start\":2.5,\"end\":7.5,\"text\":\"two\"}"),
                event("{\"id\":3,\"start\":7.5,\"end\":10.0,\"text\":\"three\"}"),
                "[DONE]");
        server.expect(requestTo(URL))
                .andExpect(content().string(matchesPattern(formField("stream", "true"))))
                .andRespond(withSuccess(body, MediaType.TEXT_EVENT_STREAM));
        var reported = new ArrayList<Integer>();

        var segments = transcriber.transcribe(wav, Language.ENGLISH, reported::add);

        assertThat(segments).hasSize(3);
        assertThat(reported).containsExactly(0, 25, 75, 99, 100);
        server.verify();
    }

    @Test
    void requestsWordTimestampsWithoutVadAndBuildsCuesFromWords() {
        var body = sse(event("""
                {"id":1,"start":27.3,"end":46.9,"text":" Bởi đây với tôi","words":[\
                {"start":27.3,"end":27.9,"word":" Bởi"},\
                {"start":45.0,"end":45.2,"word":" đây"},\
                {"start":45.2,"end":45.4,"word":" với"},\
                {"start":45.4,"end":45.9,"word":" tôi"}]},\
                {"id":2,"start":50.0,"end":51.0,"text":" Zither Harp","words":[\
                {"start":50.0,"end":50.5,"word":" Zither"},\
                {"start":50.5,"end":51.0,"word":" Harp"}]}"""));
        server.expect(requestTo(URL))
                .andExpect(content().string(allOf(
                        matchesPattern(formField("vad_filter", "false")),
                        matchesPattern(formField("timestamp_granularities\\[]", "word")),
                        matchesPattern(formField("language", "vi")))))
                .andRespond(withSuccess(body, MediaType.TEXT_EVENT_STREAM));

        var segments = transcriber.transcribe(wav, Language.VIETNAMESE);

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().index()).isEqualTo(1);
        assertThat(segments.getFirst().startTimeMs()).isEqualTo(45000);
        assertThat(segments.getFirst().endTimeMs()).isEqualTo(45900);
        assertThat(segments.getFirst().text()).isEqualTo("Bởi đây với tôi");
        server.verify();
    }

    @Test
    void sendsNoPromptForChineseAndConvertsTraditionalToSimplified() {
        var body = sse(event("""
                {"id":1,"start":1.0,"end":3.0,"text":"我曾經擁有著的一切","words":[\
                {"start":1.0,"end":1.5,"word":"我曾經"},\
                {"start":1.5,"end":2.2,"word":"擁有著的"},\
                {"start":2.2,"end":3.0,"word":"一切"}]},\
                {"id":2,"start":4.0,"end":6.0,"text":"轉眼都飄散","words":[]},\
                {"id":3,"start":7.0,"end":8.0,"text":"以下是普通話的句子。","words":[]}"""));
        server.expect(requestTo(URL))
                .andExpect(content().string(allOf(
                        matchesPattern(formField("language", "zh")),
                        not(containsString("name=\"prompt\"")))))
                .andRespond(withSuccess(body, MediaType.TEXT_EVENT_STREAM));

        var segments = transcriber.transcribe(wav, Language.CHINESE);

        assertThat(segments).extracting(SubtitleSegment::text).containsExactly("我曾经拥有着的一切", "转眼都飘散");
        server.verify();
    }

    @Test
    void reportsZeroProgressWhenAudioDurationIsUnknown() throws Exception {
        var unknown = Files.write(tempDir.resolve("raw.wav"), new byte[] {1, 2, 3});
        server.expect(requestTo(URL)).andRespond(withSuccess(
                sse(event("{\"id\":1,\"start\":0.0,\"end\":2.5,\"text\":\"one\"}")), MediaType.TEXT_EVENT_STREAM));
        var reported = new ArrayList<Integer>();

        transcriber.transcribe(unknown, Language.ENGLISH, reported::add);

        assertThat(reported).containsExactly(0, 100);
    }

    private static String formField(String namePattern, String value) {
        return "(?s).*name=\"" + namePattern + "\"[^\\r\\n]*\\R(?:[^\\r\\n]+\\R)*\\R" + value + "\\R.*";
    }

    @Test
    void rejectsMissingWav() {
        assertThatThrownBy(() -> transcriber.transcribe(tempDir.resolve("missing.wav"), null))
                .isInstanceOf(SinAiException.class)
                .extracting(e -> ((SinAiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void mapsServerErrorToTranscriptionError() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> transcriber.transcribe(wav, null))
                .isInstanceOf(SinAiException.class)
                .extracting(e -> ((SinAiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TRANSCRIPTION_ERROR);
    }
}
