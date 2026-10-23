package com.sinai.common.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubtitleSegmentTest {

    @Test
    void validSegmentComputesDuration() {
        var segment = new SubtitleSegment(1, 1000, 2500, "Hello");
        assertThat(segment.durationMs()).isEqualTo(1500);
    }

    @ParameterizedTest
    @CsvSource({
        "0,0,10,text",
        "1,-1,10,text",
        "1,10,10,text",
        "1,10,5,text",
        "1,0,10,",
        "1,0,10,'   '"
    })
    void invalidThrows(int index, long start, long end, String text) {
        assertThatThrownBy(() -> new SubtitleSegment(index, start, end, text))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SUBTITLE_SEGMENT));
    }
}
