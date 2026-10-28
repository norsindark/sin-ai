package com.sinai.identity.service;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.Role;
import com.sinai.identity.model.request.UpdateUserRequest;
import com.sinai.identity.model.response.UserPageResponse;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.repository.UserAccountRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    static final int MAX_PAGE_SIZE = 100;

    private final UserAccountRepository userRepository;

    @Transactional(readOnly = true)
    public UserPageResponse listUsers(int page, int size) {
        if (page < 0) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        var users = userRepository.findAll(PageRequest.of(page, size, sort));
        var items = users.getContent().stream().map(AuthService::toResponse).toList();
        return new UserPageResponse(items, users.getNumber(), users.getSize(), users.getTotalElements(),
                users.getTotalPages());
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        if (Objects.isNull(request) || (Objects.isNull(request.role()) && Objects.isNull(request.enabled()))) {
            throw new SinAiException(ErrorCode.INVALID_ARGUMENT, "role or enabled must be provided");
        }
        var activeAdmins = userRepository.lockEnabledByRole(Role.ADMIN);
        var user = userRepository.findForUpdateById(id)
                .orElseThrow(() -> new SinAiException(ErrorCode.USER_NOT_FOUND, "User not found: " + id));
        var wasActiveAdmin = user.isActiveAdmin();
        if (Objects.nonNull(request.role())) {
            user.changeRole(request.role());
        }
        if (Objects.nonNull(request.enabled())) {
            user.changeEnabled(request.enabled());
        }
        if (wasActiveAdmin && !user.isActiveAdmin() && activeAdmins.size() <= 1) {
            throw new SinAiException(ErrorCode.LAST_ADMIN);
        }
        return AuthService.toResponse(user);
    }
}
