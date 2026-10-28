package com.sinai.identity.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

@RequiredArgsConstructor
public class ObservabilityTokenAuthenticationProvider implements AuthenticationProvider {

    static final String PRINCIPAL = "observability";

    private final ObservabilityTokenProperties properties;

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!properties.enabled() || !(authentication instanceof BearerTokenAuthenticationToken bearer)) {
            return null;
        }
        var expected = properties.token().getBytes(StandardCharsets.UTF_8);
        var actual = bearer.getToken().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            return null;
        }
        return UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.OBSERVABILITY_ROLE)));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
