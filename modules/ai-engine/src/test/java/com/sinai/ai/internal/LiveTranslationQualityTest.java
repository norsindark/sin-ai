package com.sinai.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sinai.ai.config.AiConfig;
import com.sinai.ai.config.AiProperties;
import com.sinai.ai.internal.llm.OllamaSubtitleTranslator;
import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LiveTranslationQualityTest {

    private static final Pattern TIMING = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3}) --> (\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

    @Test
    void translatesSubtitleFileWithLiveOllama() throws Exception {
        var srtPath = System.getenv("SINAI_TEST_SRT");
        Assumptions.assumeTrue(StringUtils.isNotBlank(srtPath), "SINAI_TEST_SRT is not set");
        var source = Language.fromCode(System.getenv("SINAI_TEST_SOURCE"));
        var target = Language.fromCode(System.getenv("SINAI_TEST_TARGET"));
        var output = Path.of(System.getenv("SINAI_TEST_OUT"));
        var segments = parse(Files.readString(Path.of(srtPath)));
        var properties = new AiProperties(null, null);
        var restClient = new AiConfig().ollamaRestClient(RestClient.builder(), properties);
        var translator = new OllamaSubtitleTranslator(restClient, properties, ObservationRegistry.NOOP);

        var startedAt = System.nanoTime();
        var translated = translator.translate(segments, source, target);
        var elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        var report = new StringBuilder("elapsedMs=" + elapsedMs + "\n");
        for (var index = 0; index < segments.size(); index++) {
            report.append(segments.get(index).index()).append(" | ")
                    .append(segments.get(index).text()).append(" | ")
                    .append(translated.get(index).text()).append("\n");
        }
        Files.writeString(output, report.toString());
        assertThat(translated).hasSameSizeAs(segments);
    }

    private static List<SubtitleSegment> parse(String srt) {
        var result = new ArrayList<SubtitleSegment>();
        for (var block : srt.strip().split("\\R\\R+")) {
            var lines = block.split("\\R");
            if (lines.length < 3) {
                continue;
            }
            var matcher = TIMING.matcher(lines[1]);
            if (!matcher.find()) {
                continue;
            }
            var text = String.join(" ", List.of(lines).subList(2, lines.length));
            result.add(new SubtitleSegment(result.size() + 1, millis(matcher, 1), millis(matcher, 5), text));
        }
        return result;
    }

    private static long millis(Matcher matcher, int group) {
        return Long.parseLong(matcher.group(group)) * 3_600_000
                + Long.parseLong(matcher.group(group + 1)) * 60_000
                + Long.parseLong(matcher.group(group + 2)) * 1_000
                + Long.parseLong(matcher.group(group + 3));
    }
}
