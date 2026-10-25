package com.sinai.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sinai.ai.config.AiConfig;
import com.sinai.ai.config.AiProperties;
import com.sinai.ai.internal.llm.OllamaSubtitleTranslator;
import com.sinai.ai.internal.whisper.WhisperTranscriber;
import com.sinai.common.constant.Language;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LiveAiEngineIntegrationTest {

    @Test
    void transcribesWavAndTranslatesWithLiveServices() {
        var wavPath = System.getenv("SINAI_TEST_WAV");
        Assumptions.assumeTrue(StringUtils.isNotBlank(wavPath), "SINAI_TEST_WAV is not set");
        var wav = Path.of(wavPath);
        Assumptions.assumeTrue(Files.isRegularFile(wav), "SINAI_TEST_WAV does not exist");
        var properties = new AiProperties(null, null);
        var config = new AiConfig();
        var transcriber = new WhisperTranscriber(config.whisperRestClient(properties), properties);
        var translator = new OllamaSubtitleTranslator(config.ollamaRestClient(properties), properties);

        var segments = transcriber.transcribe(wav, Language.ENGLISH);
        var translated = translator.translate(segments, Language.ENGLISH, Language.VIETNAMESE);

        assertThat(segments).isNotEmpty();
        assertThat(translated).hasSameSizeAs(segments);
    }
}
