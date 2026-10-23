package com.sinai.common.constant;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

@Getter
public enum FileType {
    VIDEO("mp4", "video/mp4"),
    AUDIO("wav", "audio/wav"),
    SUBTITLE("srt", "application/x-subrip");

    private final String extension;
    private final String contentType;

    FileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public static FileType fromExtension(String extension) {
        var normalized = Strings.CS.removeStart(StringUtils.trim(extension), ".");
        if (StringUtils.isBlank(normalized)) {
            throw new SinAiException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        for (var fileType : values()) {
            if (fileType.extension.equalsIgnoreCase(normalized)) {
                return fileType;
            }
        }
        throw new SinAiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Unsupported file type: " + normalized);
    }
}
