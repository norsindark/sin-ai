package com.sinai.identity.config;

import com.sinai.common.dto.ApiErrorResponse;
import com.sinai.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

public class JsonSecurityErrorWriter {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            write(response, ErrorCode.UNAUTHORIZED);
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse(errorCode, errorCode.getDefaultMessage()));
    }
}
