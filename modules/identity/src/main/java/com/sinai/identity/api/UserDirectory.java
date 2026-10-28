package com.sinai.identity.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserDirectory {

    UserCounts countUsers(Instant newSince);

    List<UserSummary> newestUsers(int limit);

    Map<UUID, String> usernamesByIds(Collection<UUID> userIds);

    record UserCounts(long total, long enabled, long activeAdmins, long newSince) {
    }

    record UserSummary(UUID id, String username, Role role, boolean enabled, Instant createdAt) {
    }
}
