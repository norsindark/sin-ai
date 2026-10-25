package com.sinai.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sinai.ai.config.AiProperties;
import com.sinai.ai.internal.whisper.WhisperTranscriber;
import com.sinai.common.constant.Language;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WhisperTranscriberTest {

    private static final String URL = "http://whisper/v1/audio/transcriptions";

    @TempDir
    Path tempDir;

    private MockRestServiceServer server;
    private WhisperTranscriber transcriber;
    private Path wav;

    @BeforeEach
    void setUp() throws Exception {
        var builder = RestClient.builder().baseUrl("http://whisper");
        server = MockRestServiceServer.bindTo(builder).build();
        transcriber = new WhisperTranscriber(builder.build(), new AiProperties(null, null));
        wav = Files.write(tempDir.resolve("a.wav"), new byte[] {1, 2, 3});
    }

    @Test
    void mapsFiltersAndRenumbersSegments() {
        var body = """
                {"text":"x","language":"en","segments":[
                {"id":0,"start":0.0,"end":1.2344,"text":"  Hello  "},
                {"id":1,"start":1.3,"end":2.0,"text":"   "},
                {"id":2,"start":3.0004,"end":3.0001,"text":"Zero length"},
                {"id":3,"start":4.5,"end":6.0,"text":"World"}]}
                """;
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

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
