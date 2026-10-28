package com.sinai.identity.controller;

import com.sinai.identity.api.UserApi;
import com.sinai.identity.model.request.UpdateUserRequest;
import com.sinai.identity.model.response.UserPageResponse;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.service.UserAdminService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserAdminService userAdminService;

    @Override
    public UserPageResponse listUsers(int page, int size) {
        return userAdminService.listUsers(page, size);
    }

    @Override
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        return userAdminService.updateUser(id, request);
    }
}
