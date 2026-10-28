package com.sinai.ai.internal.whisper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HallucinationFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"Zither Harp", "奥参马VIAVIAVIA", "Thank you for watching!", "以下是普通话的句子。", "  "})
    void flagsKnownHallucinations(String text) {
        assertThat(HallucinationFilter.isHallucination(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"我曾经跨过山河大海", "Ngày thay đêm vội trôi", "Thank you, my friend", "la la la",
            "宝贝宝贝宝贝", "啦啦啦啦啦啦", "1000000"})
    void keepsRealLyrics(String text) {
        assertThat(HallucinationFilter.isHallucination(text)).isFalse();
    }
}
