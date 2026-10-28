package com.sinai.identity.config;

import com.sinai.identity.repository.TokenStateRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.CollectionUtils;

@RequiredArgsConstructor
public class ActiveTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult INACTIVE = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token is revoked or no longer matches its user",
                    null));

    private final TokenStateRepository tokenStateRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        var userId = parseUuid(jwt.getSubject());
        var jti = parseUuid(jwt.getId());
        var roles = jwt.getClaimAsStringList(SecurityConfig.ROLES_CLAIM);
        var tokenVersion = jwt.getClaim(SecurityConfig.TOKEN_VERSION_CLAIM) instanceof Number version
                ? Optional.of(version.intValue())
                : Optional.<Integer>empty();
        if (userId.isEmpty() || jti.isEmpty() || tokenVersion.isEmpty() || CollectionUtils.isEmpty(roles)) {
            return INACTIVE;
        }
        var active = tokenStateRepository.findSubjectState(userId.get(), jti.get())
                .filter(state -> state.enabled() && !state.revoked())
                .filter(state -> state.tokenVersion() == tokenVersion.get())
                .filter(state -> roles.size() == 1 && state.role().name().equals(roles.getFirst()))
                .isPresent();
        return active ? OAuth2TokenValidatorResult.success() : INACTIVE;
    }

    static Optional<UUID> parseUuid(String value) {
        if (StringUtils.isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
