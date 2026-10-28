package com.sinai.ai.config;

import java.time.Duration;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.ai")
public record AiProperties(Whisper whisper, Ollama ollama) {

    public AiProperties {
        if (Objects.isNull(whisper)) {
            whisper = new Whisper(null, null, null, false);
        }
        if (Objects.isNull(ollama)) {
            ollama = new Ollama(null, null, null, 0);
        }
    }

    public record Whisper(String baseUrl, String model, Duration timeout, boolean vadFilter) {

        public Whisper {
            if (StringUtils.isBlank(baseUrl)) {
                baseUrl = "http://localhost:8000";
            }
            if (StringUtils.isBlank(model)) {
                model = "Systran/faster-whisper-small";
            }
            if (Objects.isNull(timeout)) {
                timeout = Duration.ofMinutes(30);
            }
        }
    }

    public record Ollama(String baseUrl, String model, Duration timeout, int batchSize) {

        public Ollama {
            if (StringUtils.isBlank(baseUrl)) {
                baseUrl = "http://localhost:11434";
            }
            if (StringUtils.isBlank(model)) {
                model = "qwen2.5:3b";
            }
            if (Objects.isNull(timeout)) {
                timeout = Duration.ofMinutes(5);
            }
            if (batchSize < 1) {
                batchSize = 8;
            }
        }
    }
}
