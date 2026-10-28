package com.sinai.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INTERNAL_ERROR(500, "Internal error"),
    INVALID_ARGUMENT(400, "Invalid argument"),
    NOT_FOUND(404, "Resource not found"),
    JOB_NOT_FOUND(404, "Job not found"),
    FILE_TOO_LARGE(413, "File too large"),
    UNSUPPORTED_LANGUAGE(400, "Unsupported language"),
    UNSUPPORTED_FILE_TYPE(400, "Unsupported file type"),
    INVALID_SUBTITLE_SEGMENT(400, "Invalid subtitle segment"),
    INVALID_SRT_TIMESTAMP(400, "Invalid SRT timestamp"),
    WORKSPACE_IO_ERROR(500, "Workspace I/O error"),
    STORAGE_ERROR(502, "Storage error"),
    MEDIA_PROCESSING_ERROR(500, "Media processing error"),
    TRANSCRIPTION_ERROR(502, "Transcription error"),
    TRANSLATION_ERROR(502, "Translation error"),
    NO_SPEECH_DETECTED(422, "No speech detected in the video"),
    JOB_INTERRUPTED(500, "Job was interrupted before completion"),
    JOB_IN_PROGRESS(409, "Job is still processing"),
    UNAUTHORIZED(401, "Authentication required"),
    FORBIDDEN(403, "Access denied"),
    INVALID_CREDENTIALS(401, "Invalid username or password"),
    USERNAME_ALREADY_EXISTS(409, "Username is already registered"),
    EMAIL_ALREADY_EXISTS(409, "Email is already registered"),
    USER_NOT_FOUND(404, "User not found"),
    LAST_ADMIN(409, "At least one enabled admin is required");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
