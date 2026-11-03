package com.sinai.app.config;

import static com.sinai.app.config.TestJwts.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.api.CurrentUserProvider;
import com.sinai.identity.api.Role;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.controller.UserController;
import com.sinai.identity.model.request.UpdateUserRequest;
import com.sinai.identity.model.response.UserPageResponse;
import com.sinai.identity.model.response.UserResponse;
import com.sinai.identity.repository.TokenStateRepository;
import com.sinai.identity.service.UserAdminService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class, properties = {
        TestJwts.JWT_SECRET_PROPERTY,
        "sinai.security.observability.token=" + UserApiWebMvcTest.OBSERVABILITY_TOKEN})
@Import({GlobalExceptionHandler.class, SecurityConfig.class, CurrentUserProvider.class})
class UserApiWebMvcTest {

    static final String OBSERVABILITY_TOKEN = "observability-token-0123456789abcdef";

    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), "root", Role.ADMIN);
    private static final CurrentUser USER = new CurrentUser(UUID.randomUUID(), "alice", Role.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenStateRepository tokenStateRepository;

    @MockitoBean
    private UserAdminService userAdminService;

    @Test
    void anonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(userAdminService);
    }

    @Test
    void regularUserIsForbiddenOnEveryEndpoint() throws Exception {
        var id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/users").with(as(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(patch("/api/v1/users/{id}", id).with(as(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/{id}/password", id).with(as(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/{id}", id).with(as(USER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userAdminService);
    }

    @Test
    void observabilityTokenCannotManageUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + OBSERVABILITY_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(userAdminService);
    }

    @Test
    void adminListsUsersWithDefaultPaging() throws Exception {
        when(userAdminService.listUsers(0, 20))
                .thenReturn(new UserPageResponse(List.of(sampleUser(Role.USER, true)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("alice"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminUpdatesRoleAndEnabledFlag() throws Exception {
        var user = sampleUser(Role.ADMIN, false);
        when(userAdminService.updateUser(eq(user.id()), any())).thenReturn(user);

        mockMvc.perform(patch("/api/v1/users/{id}", user.id()).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(false));

        var captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(userAdminService).updateUser(eq(user.id()), captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().enabled()).isFalse();
    }

    @Test
    void demotingLastAdminReturnsConflict() throws Exception {
        when(userAdminService.updateUser(any(), any())).thenThrow(new SinAiException(ErrorCode.LAST_ADMIN));

        mockMvc.perform(patch("/api/v1/users/{id}", ADMIN.id()).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void updatingUnknownUserReturnsNotFound() throws Exception {
        when(userAdminService.updateUser(any(), any())).thenThrow(new SinAiException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void adminDeletesUserAsTheCallerFromTheToken() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/{id}", id).with(as(ADMIN)))
                .andExpect(status().isNoContent());

        verify(userAdminService).deleteUser(id, ADMIN);
    }

    @Test
    void deletingSelfReturnsConflict() throws Exception {
        doThrow(new SinAiException(ErrorCode.CANNOT_DELETE_SELF))
                .when(userAdminService).deleteUser(eq(ADMIN.id()), any());

        mockMvc.perform(delete("/api/v1/users/{id}", ADMIN.id()).with(as(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_SELF"));
    }

    @Test
    void malformedIdReturnsInvalidArgument() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", "not-a-uuid").with(as(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        verifyNoInteractions(userAdminService);
    }

    private static UserResponse sampleUser(Role role, boolean enabled) {
        return new UserResponse(USER.id(), USER.username(), "alice@example.com", role, enabled,
                Instant.parse("2026-10-25T00:00:00Z"));
    }
}
