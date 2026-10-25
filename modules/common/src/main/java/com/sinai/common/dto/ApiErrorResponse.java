package com.sinai.common.dto;

import com.sinai.common.exception.ErrorCode;

public record ApiErrorResponse(ErrorCode code, String message) {
}
