package com.sinai.identity.api;

import java.util.UUID;

public record UserDeletionEvent(UUID deletedUserId, UUID successorId) {
}
