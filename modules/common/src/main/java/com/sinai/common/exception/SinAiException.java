package com.sinai.common.exception;

import lombok.Getter;

@Getter
public class SinAiException extends RuntimeException {

    private final ErrorCode errorCode;

    public SinAiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public SinAiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SinAiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
