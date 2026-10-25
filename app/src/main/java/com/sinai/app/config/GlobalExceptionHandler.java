package com.sinai.app.config;

import com.sinai.common.dto.ApiErrorResponse;
import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SinAiException.class)
    public ResponseEntity<ApiErrorResponse> handleSinAiException(SinAiException exception) {
        var errorCode = exception.getErrorCode();
        var status = HttpStatus.valueOf(errorCode.getHttpStatus());
        if (status.is5xxServerError()) {
            log.error("Request failed with {}", errorCode, exception);
            return build(status, errorCode, errorCode.getDefaultMessage());
        }
        return build(status, errorCode, StringUtils.defaultIfBlank(exception.getMessage(),
                errorCode.getDefaultMessage()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(MultipartException exception) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, "Invalid multipart request");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        var errorCode = toErrorCode(statusCode);
        var message = errorCode.getDefaultMessage();
        var problemDetail = resolveProblemDetail(exception, body);
        if (statusCode.is4xxClientError() && Objects.nonNull(problemDetail)
                && StringUtils.isNotBlank(problemDetail.getDetail())) {
            message = problemDetail.getDetail();
        }
        if (statusCode.is5xxServerError()) {
            log.error("Request failed with status {}", statusCode, exception);
        }
        return ResponseEntity.status(statusCode).headers(headers).body(new ApiErrorResponse(errorCode, message));
    }

    private static ProblemDetail resolveProblemDetail(Exception exception, Object body) {
        if (body instanceof ProblemDetail problemDetail) {
            return problemDetail;
        }
        if (exception instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody();
        }
        return null;
    }

    static ErrorCode toErrorCode(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        if (statusCode.value() == HttpStatus.CONTENT_TOO_LARGE.value()) {
            return ErrorCode.FILE_TOO_LARGE;
        }
        if (statusCode.is4xxClientError()) {
            return ErrorCode.INVALID_ARGUMENT;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, ErrorCode errorCode, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(errorCode, message));
    }
}
