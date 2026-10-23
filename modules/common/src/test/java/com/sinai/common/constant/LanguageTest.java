package com.sinai.common.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTest {

    @ParameterizedTest
    @CsvSource({"en,ENGLISH", "EN,ENGLISH", "' vi ',VIETNAMESE", "Ja,JAPANESE"})
    void resolvesCode(String code, Language expected) {
        assertThat(Language.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "xx", "english"})
    void invalidThrows(String code) {
        assertThatThrownBy(() -> Language.fromCode(code))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_LANGUAGE));
    }
}
