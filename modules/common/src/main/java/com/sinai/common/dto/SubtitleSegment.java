package com.sinai.common.dto;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import org.apache.commons.lang3.StringUtils;

public record SubtitleSegment(int index, long startTimeMs, long endTimeMs, String text) {

    public SubtitleSegment {
        if (index < 1 || startTimeMs < 0 || endTimeMs <= startTimeMs || StringUtils.isBlank(text)) {
            throw new SinAiException(ErrorCode.INVALID_SUBTITLE_SEGMENT);
        }
    }

    public long durationMs() {
        return endTimeMs - startTimeMs;
    }
}
