package com.sinai.identity.api;

import java.util.UUID;

public record CurrentUser(UUID id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
