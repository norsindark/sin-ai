package com.sinai.identity.controller;

import com.sinai.identity.api.AuthApi;
import com.sinai.identity.api.CurrentUserProvider;
import com.sinai.identity.model.request.ChangeEmailRequest;
import com.sinai.identity.model.request.ChangePasswordRequest;
import com.sinai.identity.model.request.LoginRequest;
import com.sinai.identity.model.request.RegisterRequest;
import com.sinai.identity.model.response.TokenResponse;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;
    private final RefreshCookies refreshCookies;
    private final HttpServletRequest httpRequest;

    @Override
    public ResponseEntity<UserResponse> register(RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, httpRequest.getRemoteAddr()));
    }

    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest request) {
        return refreshCookies.withSession(HttpStatus.OK, authService.login(request, httpRequest.getRemoteAddr()));
    }

    @Override
    public ResponseEntity<TokenResponse> refresh(String refreshToken) {
        return refreshCookies.withSession(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @Override
    public ResponseEntity<Void> logout(String refreshToken) {
        authService.logout(currentUserProvider.requireToken(), refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.cleared()).build();
    }

    @Override
    public UserResponse me() {
        return authService.me(currentUserProvider.require());
    }

    @Override
    public ResponseEntity<TokenResponse> changePassword(ChangePasswordRequest request) {
        return refreshCookies.withSession(HttpStatus.OK,
                authService.changePassword(currentUserProvider.require(), request, httpRequest.getRemoteAddr()));
    }

    @Override
    public ResponseEntity<TokenResponse> changeEmail(ChangeEmailRequest request) {
        return refreshCookies.withSession(HttpStatus.OK,
                authService.changeEmail(currentUserProvider.require(), request, httpRequest.getRemoteAddr()));
    }
}
