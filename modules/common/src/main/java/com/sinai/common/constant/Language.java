package com.sinai.common.constant;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum Language {
    ENGLISH("en", "English"),
    VIETNAMESE("vi", "Vietnamese"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    CHINESE("zh", "Chinese"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    SPANISH("es", "Spanish");

    private final String code;
    private final String displayName;

    Language(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static Language fromCode(String code) {
        var normalized = StringUtils.trim(code);
        if (StringUtils.isBlank(normalized)) {
            throw new SinAiException(ErrorCode.UNSUPPORTED_LANGUAGE);
        }
        for (var language : values()) {
            if (language.code.equalsIgnoreCase(normalized)) {
                return language;
            }
        }
        throw new SinAiException(ErrorCode.UNSUPPORTED_LANGUAGE, "Unsupported language: " + normalized);
    }
}
