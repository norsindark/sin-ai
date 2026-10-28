package com.sinai.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.api.Role;
import com.sinai.identity.api.UserDeletionEvent;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.model.request.ResetPasswordRequest;
import com.sinai.identity.model.request.UpdateUserRequest;
import com.sinai.identity.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class UserAdminServiceTest {

    private UserAccountRepository userRepository;
    private UserAdminService service;
    private SessionService sessionService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserAccountRepository.class);
        sessionService = mock(SessionService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new UserAdminService(userRepository, PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                sessionService, eventPublisher);
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

    @Test
    void resetPasswordChangesTheHashBumpsTheTokenVersionAndEndsAllSessions() {
        var user = UserAccount.create("user", "user@example.com", "{noop}old-password", Role.USER);
        when(userRepository.findForUpdateById(user.getId())).thenReturn(Optional.of(user));

        service.resetPassword(user.getId(), new ResetPasswordRequest("brand-new-password"));

        assertThat(user.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(sessionService).endAll(user.getId());
        assertThatThrownBy(() -> service.resetPassword(user.getId(), new ResetPasswordRequest("short")))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void deleteUserTransfersOwnershipToTheCallingAdminBeforeDeletingTheRow() {
        var admin = UserAccount.create("admin", "admin@example.com", "{noop}x", Role.ADMIN);
        var user = UserAccount.create("user", "user@example.com", "{noop}x", Role.USER);
        when(userRepository.lockEnabledByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findForUpdateById(user.getId())).thenReturn(Optional.of(user));

        service.deleteUser(user.getId(), new CurrentUser(admin.getId(), "admin", Role.ADMIN));

        var order = inOrder(eventPublisher, userRepository);
        order.verify(eventPublisher).publishEvent(new UserDeletionEvent(user.getId(), admin.getId()));
        order.verify(userRepository).delete(user);
    }

    @Test
    void deleteUserRefusesSelfLastAdminAndCallersThatAreNoLongerActiveAdmins() {
        var admin = UserAccount.create("admin", "admin@example.com", "{noop}x", Role.ADMIN);
        var otherAdmin = UserAccount.create("other", "other@example.com", "{noop}x", Role.ADMIN);
        var caller = new CurrentUser(admin.getId(), "admin", Role.ADMIN);
        when(userRepository.findForUpdateById(otherAdmin.getId())).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.deleteUser(admin.getId(), caller))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CANNOT_DELETE_SELF));
        when(userRepository.lockEnabledByRole(Role.ADMIN)).thenReturn(List.of(otherAdmin));
        assertThatThrownBy(() -> service.deleteUser(otherAdmin.getId(), caller))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(userRepository, never()).delete(any(UserAccount.class));
    }
}
