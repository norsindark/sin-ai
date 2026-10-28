package com.sinai.identity.api;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.config.SecurityConfig;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class CurrentUserProvider {

    public CurrentUser require() {
        var jwt = requireToken();
        var roles = jwt.getClaimAsStringList(SecurityConfig.ROLES_CLAIM);
        var role = CollectionUtils.isEmpty(roles) ? null : EnumUtils.getEnum(Role.class, roles.getFirst());
        if (Objects.isNull(role) || StringUtils.isBlank(jwt.getSubject())) {
            throw new SinAiException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return new CurrentUser(UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM), role);
        } catch (IllegalArgumentException e) {
            throw new SinAiException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage(), e);
        }
    }

    public Jwt requireToken() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            throw new SinAiException(ErrorCode.UNAUTHORIZED);
        }
        return token.getToken();
    }
}
