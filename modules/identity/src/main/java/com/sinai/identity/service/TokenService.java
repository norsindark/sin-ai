package com.sinai.identity.service;

import com.sinai.identity.config.JwtProperties;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.model.entity.UserAccount;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock = Clock.systemUTC();

    public String issueAccessToken(UserAccount user) {
        var issuedAt = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim(SecurityConfig.USERNAME_CLAIM, user.getUsername())
                .claim(SecurityConfig.EMAIL_CLAIM, user.getEmail())
                .claim(SecurityConfig.ROLES_CLAIM, List.of(user.getRole().name()))
                .claim(SecurityConfig.TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }
}
