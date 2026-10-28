package com.sinai.common.exception;

import java.time.Duration;
import lombok.Getter;

@Getter
public class TooManyAttemptsException extends SinAiException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_ATTEMPTS);
        this.retryAfter = retryAfter;
    }
}
