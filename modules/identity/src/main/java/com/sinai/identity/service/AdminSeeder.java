package com.sinai.identity.service;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.Role;
import com.sinai.identity.config.AdminSeedProperties;
import com.sinai.identity.repository.UserAccountRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final Set<ErrorCode> TAKEN = Set.of(ErrorCode.USERNAME_ALREADY_EXISTS,
            ErrorCode.EMAIL_ALREADY_EXISTS);

    private final AdminSeedProperties properties;
    private final UserAccountRepository userRepository;
    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        if (StringUtils.isAllBlank(properties.username(), properties.email(), properties.password())) {
            return;
        }
        if (StringUtils.isAnyBlank(properties.username(), properties.email(), properties.password())) {
            log.warn("Admin seed skipped: sinai.security.admin.username, email and password are all required");
            return;
        }
        var username = CredentialPolicy.validateUsername(properties.username());
        var email = CredentialPolicy.validateEmail(properties.email());
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            log.info("Admin seed skipped: account {} already exists and is left unchanged", username);
            return;
        }
        try {
            authService.createUser(username, email, CredentialPolicy.validatePassword(properties.password()),
                    Role.ADMIN);
            log.info("Seeded admin account {}", username);
        } catch (SinAiException e) {
            if (!TAKEN.contains(e.getErrorCode())) {
                throw e;
            }
            log.info("Admin seed skipped: account {} was created concurrently", username);
        }
    }
}
