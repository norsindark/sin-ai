package com.sinai.common.util;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public final class SrtTimeFormatter {

    private static final Pattern TIMESTAMP = Pattern.compile("^(\\d{2,}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})$");
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private SrtTimeFormatter() {
    }

    public static String format(long millis) {
        if (millis < 0) {
            throw new SinAiException(ErrorCode.INVALID_SRT_TIMESTAMP, "Negative timestamp: " + millis);
        }
        var hours = millis / MILLIS_PER_HOUR;
        var minutes = (millis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE;
        var seconds = (millis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND;
        var remainder = millis % MILLIS_PER_SECOND;
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", hours, minutes, seconds, remainder);
    }

    public static long parse(String timestamp) {
        var normalized = StringUtils.trim(timestamp);
        if (StringUtils.isBlank(normalized)) {
            throw new SinAiException(ErrorCode.INVALID_SRT_TIMESTAMP);
        }
        var matcher = TIMESTAMP.matcher(normalized);
        if (!matcher.matches()) {
            throw new SinAiException(ErrorCode.INVALID_SRT_TIMESTAMP, "Invalid timestamp: " + normalized);
        }
        try {
            var hours = Math.multiplyExact(Long.parseLong(matcher.group(1)), MILLIS_PER_HOUR);
            var minutes = Long.parseLong(matcher.group(2)) * MILLIS_PER_MINUTE;
            var seconds = Long.parseLong(matcher.group(3)) * MILLIS_PER_SECOND;
            var millis = Long.parseLong(matcher.group(4));
            return Math.addExact(hours, minutes + seconds + millis);
        } catch (ArithmeticException | NumberFormatException e) {
            throw new SinAiException(ErrorCode.INVALID_SRT_TIMESTAMP, "Invalid timestamp: " + normalized, e);
        }
    }
}
