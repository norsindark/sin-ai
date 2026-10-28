package com.sinai.identity.service;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public final class CredentialPolicy {

    static final int MIN_PASSWORD_BYTES = 8;
    static final int MAX_PASSWORD_BYTES = 72;
    static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,32}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private CredentialPolicy() {
    }

    public static String normalizeUsername(String rawUsername) {
        return StringUtils.lowerCase(StringUtils.trim(rawUsername), Locale.ROOT);
    }

    public static String validateUsername(String rawUsername) {
        var username = normalizeUsername(rawUsername);
        if (StringUtils.isBlank(username)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "username must not be blank");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT,
                    "username must be 3-32 characters of a-z, 0-9, '.', '_' or '-'");
        }
        return username;
    }

    public static String normalizeEmail(String rawEmail) {
        return StringUtils.lowerCase(StringUtils.trim(rawEmail), Locale.ROOT);
    }

    public static String validateEmail(String rawEmail) {
        var email = normalizeEmail(rawEmail);
        if (StringUtils.isBlank(email)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "email must not be blank");
        }
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "email is not valid");
        }
        return email;
    }

    public static boolean looksLikeEmail(String login) {
        return StringUtils.contains(login, '@');
    }

    public static String validatePassword(String password) {
        if (StringUtils.isBlank(password)) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "password must not be blank");
        }
        var byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_PASSWORD_BYTES || byteLength > MAX_PASSWORD_BYTES) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "password must be between " + MIN_PASSWORD_BYTES
                    + " and " + MAX_PASSWORD_BYTES + " UTF-8 bytes");
        }
        return password;
    }
}
