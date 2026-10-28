package com.sinai.identity.controller;

import com.sinai.identity.api.AuthApi;
import com.sinai.identity.api.CurrentUserProvider;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.model.response.TokenResponse;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public ResponseEntity<UserResponse> register(RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        return authService.login(request);
    }

    @Override
    public ResponseEntity<Void> logout() {
        authService.logout(currentUserProvider.requireToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public UserResponse me() {
        return authService.me(currentUserProvider.require());
    }
}
