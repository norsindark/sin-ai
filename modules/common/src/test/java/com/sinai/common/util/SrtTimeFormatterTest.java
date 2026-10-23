package com.sinai.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SrtTimeFormatterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "0|00:00:00,000",
        "1|00:00:00,001",
        "59999|00:00:59,999",
        "3600000|01:00:00,000",
        "86399999|23:59:59,999",
        "360000000|100:00:00,000"
    })
    void formatsAndParsesInverse(long millis, String expected) {
        assertThat(SrtTimeFormatter.format(millis)).isEqualTo(expected);
        assertThat(SrtTimeFormatter.parse(expected)).isEqualTo(millis);
    }

    @Test
    void formatNegativeThrows() {
        assertThatThrownBy(() -> SrtTimeFormatter.format(-1))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SRT_TIMESTAMP));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "00:00:01.500|1500",
        "'  00:00:01,500  '|1500"
    })
    void parsesDotAndTrimsWhitespace(String input, long expected) {
        assertThat(SrtTimeFormatter.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "1:00:00,000", "00:60:00,000", "00:00:60,000", "00:00:00,12",
        "00:00:00,1234", "abc", "99999999999999999999:00:00,000", "9999999999999999:00:00,000"})
    void parseInvalidThrows(String input) {
        assertThatThrownBy(() -> SrtTimeFormatter.parse(input))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SRT_TIMESTAMP));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 7, 1500, 3723004, 360000123})
    void roundTrips(long millis) {
        assertThat(SrtTimeFormatter.parse(SrtTimeFormatter.format(millis))).isEqualTo(millis);
    }
}
