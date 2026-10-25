package com.sinai.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INTERNAL_ERROR(500, "Internal error"),
    INVALID_ARGUMENT(400, "Invalid argument"),
    UNSUPPORTED_LANGUAGE(400, "Unsupported language"),
    UNSUPPORTED_FILE_TYPE(400, "Unsupported file type"),
    INVALID_SUBTITLE_SEGMENT(400, "Invalid subtitle segment"),
    INVALID_SRT_TIMESTAMP(400, "Invalid SRT timestamp"),
    WORKSPACE_IO_ERROR(500, "Workspace I/O error"),
    STORAGE_ERROR(502, "Storage error"),
    MEDIA_PROCESSING_ERROR(500, "Media processing error"),
    TRANSCRIPTION_ERROR(502, "Transcription error"),
    TRANSLATION_ERROR(502, "Translation error");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
