package com.sinai.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sinai.security.admin")
public record AdminSeedProperties(String username, String email, String password) {
}
