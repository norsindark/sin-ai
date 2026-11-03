package com.sinai.app.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.config.SecurityConfig;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

final class TestJwts {

    static final String JWT_SECRET_PROPERTY = "sinai.security.jwt.secret=0123456789abcdef0123456789abcdef";

    private TestJwts() {
    }

    static RequestPostProcessor as(CurrentUser user) {
        return jwt()
                .jwt(token -> token.subject(user.id().toString())
                        .claim(SecurityConfig.USERNAME_CLAIM, user.username())
                        .claim(SecurityConfig.ROLES_CLAIM, List.of(user.role().name())))
                .authorities(new SimpleGrantedAuthority(user.role().authority()));
    }
}
