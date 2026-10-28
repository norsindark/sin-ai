package com.sinai.identity.controller;

import com.sinai.identity.api.CurrentUserProvider;
import com.sinai.identity.api.UserApi;
import com.sinai.identity.model.request.ResetPasswordRequest;
import com.sinai.identity.model.request.UpdateUserRequest;
import com.sinai.identity.model.response.UserPageResponse;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.service.UserAdminService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserAdminService userAdminService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public UserPageResponse listUsers(int page, int size) {
        return userAdminService.listUsers(page, size);
    }

    @Override
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        return userAdminService.updateUser(id, request);
    }

    @Override
    public UserResponse resetPassword(UUID id, ResetPasswordRequest request) {
        return userAdminService.resetPassword(id, request);
    }

    @Override
    public ResponseEntity<Void> deleteUser(UUID id) {
        userAdminService.deleteUser(id, currentUserProvider.require());
        return ResponseEntity.noContent().build();
    }
}
