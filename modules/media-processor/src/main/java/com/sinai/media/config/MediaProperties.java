package com.sinai.media.config;

import java.time.Duration;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.media")
public record MediaProperties(String ffmpegPath, Duration timeout) {

    public MediaProperties {
        if (StringUtils.isBlank(ffmpegPath)) {
            ffmpegPath = "ffmpeg";
        }
        if (Objects.isNull(timeout)) {
            timeout = Duration.ofMinutes(10);
        }
    }
}
