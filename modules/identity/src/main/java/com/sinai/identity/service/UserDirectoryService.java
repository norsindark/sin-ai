package com.sinai.identity.service;

import com.sinai.identity.api.UserDirectory;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.repository.UserAccountRepository;
import com.sinai.identity.repository.UserStatsRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class UserDirectoryService implements UserDirectory {

    private final UserAccountRepository userRepository;
    private final UserStatsRepository statsRepository;

    @Override
    public UserCounts countUsers(Instant newSince) {
        return statsRepository.countUsers(newSince);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummary> newestUsers(int limit) {
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return userRepository.findAll(PageRequest.of(0, limit, sort)).getContent().stream()
                .map(user -> new UserSummary(user.getId(), user.getUsername(), user.getRole(), user.isEnabled(),
                        user.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> usernamesByIds(Collection<UUID> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername));
    }
}
