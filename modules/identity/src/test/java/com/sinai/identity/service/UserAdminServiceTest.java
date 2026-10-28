package com.sinai.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.Role;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.model.request.UpdateUserRequest;
import com.sinai.identity.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserAdminServiceTest {

    private UserAccountRepository userRepository;
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserAccountRepository.class);
        service = new UserAdminService(userRepository);
    }

    @Test
    void updateUserChangesRoleAndEnabled() {
        var admin = UserAccount.create("admin", "admin@example.com", "{noop}x", Role.ADMIN);
        var user = UserAccount.create("user", "user@example.com", "{noop}x", Role.USER);
        when(userRepository.lockEnabledByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findForUpdateById(user.getId())).thenReturn(Optional.of(user));

        var response = service.updateUser(user.getId(), new UpdateUserRequest(Role.ADMIN, false));

        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void updateUserRefusesToDemoteOrDisableTheLastEnabledAdmin() {
        var admin = UserAccount.create("admin", "admin@example.com", "{noop}x", Role.ADMIN);
        when(userRepository.lockEnabledByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findForUpdateById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateUser(admin.getId(), new UpdateUserRequest(Role.USER, null)))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LAST_ADMIN));
        admin.changeRole(Role.ADMIN);
        assertThatThrownBy(() -> service.updateUser(admin.getId(), new UpdateUserRequest(null, false)))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LAST_ADMIN));
    }

    @Test
    void updateUserAllowsDemotingAnAdminWhenAnotherRemains() {
        var first = UserAccount.create("first", "first@example.com", "{noop}x", Role.ADMIN);
        var second = UserAccount.create("second", "second@example.com", "{noop}x", Role.ADMIN);
        when(userRepository.lockEnabledByRole(Role.ADMIN)).thenReturn(List.of(first, second));
        when(userRepository.findForUpdateById(first.getId())).thenReturn(Optional.of(first));

        assertThat(service.updateUser(first.getId(), new UpdateUserRequest(Role.USER, null)).role())
                .isEqualTo(Role.USER);
    }

    @Test
    void updateUserRejectsEmptyRequestAndUnknownUser() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> service.updateUser(id, new UpdateUserRequest(null, null)))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        when(userRepository.findForUpdateById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateUser(id, new UpdateUserRequest(Role.USER, null)))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
