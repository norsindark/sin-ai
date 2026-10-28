package com.sinai.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

class ObservabilityTokenAuthenticationProviderTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void matchingTokenGetsOnlyTheObservabilityRole() {
        var provider = new ObservabilityTokenAuthenticationProvider(new ObservabilityTokenProperties(TOKEN));

        var authentication = provider.authenticate(new BearerTokenAuthenticationToken(TOKEN));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OBSERVABILITY");
    }

    @Test
    void otherTokensFallThroughToTheNextProvider() {
        var provider = new ObservabilityTokenAuthenticationProvider(new ObservabilityTokenProperties(TOKEN));
        var disabled = new ObservabilityTokenAuthenticationProvider(new ObservabilityTokenProperties("  "));

        assertThat(provider.authenticate(new BearerTokenAuthenticationToken(TOKEN + "x"))).isNull();
        assertThat(provider.authenticate(new BearerTokenAuthenticationToken("eyJhbGciOiJIUzI1NiJ9.e30.x"))).isNull();
        assertThat(disabled.authenticate(new BearerTokenAuthenticationToken(TOKEN))).isNull();
    }

    @Test
    void propertiesRejectShortTokensAndTreatBlankAsDisabled() {
        assertThatThrownBy(() -> new ObservabilityTokenProperties("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ObservabilityTokenProperties(null).enabled()).isFalse();
        assertThat(new ObservabilityTokenProperties(" ").enabled()).isFalse();
        assertThat(new ObservabilityTokenProperties(TOKEN).enabled()).isTrue();
    }
}
