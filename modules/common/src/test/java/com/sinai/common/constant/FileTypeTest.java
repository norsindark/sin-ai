package com.sinai.common.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FileTypeTest {

    @ParameterizedTest
    @CsvSource({
        "mp4,VIDEO",
        ".MP4,VIDEO",
        "' wav ',AUDIO",
        ".srt,SUBTITLE"
    })
    void resolvesExtension(String extension, FileType expected) {
        assertThat(FileType.fromExtension(extension)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"avi", "", "."})
    void invalidThrows(String extension) {
        assertThatThrownBy(() -> FileType.fromExtension(extension))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
    }
}
