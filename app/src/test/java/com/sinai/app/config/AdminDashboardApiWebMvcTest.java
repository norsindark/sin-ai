package com.sinai.app.config;

import static com.sinai.app.config.TestJwts.as;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sinai.common.constant.JobStatus;
import com.sinai.identity.api.CurrentUser;
import com.sinai.identity.api.CurrentUserProvider;
import com.sinai.identity.api.Role;
import com.sinai.identity.config.SecurityConfig;
import com.sinai.identity.repository.TokenStateRepository;
import com.sinai.workflow.controller.AdminDashboardController;
import com.sinai.workflow.model.response.AdminDashboardResponse;
import com.sinai.workflow.model.response.AdminDashboardResponse.DailyCount;
import com.sinai.workflow.model.response.AdminDashboardResponse.JobStats;
import com.sinai.workflow.model.response.AdminDashboardResponse.RecentJob;
import com.sinai.workflow.model.response.AdminDashboardResponse.RecentUser;
import com.sinai.workflow.model.response.AdminDashboardResponse.StatusCount;
import com.sinai.workflow.model.response.AdminDashboardResponse.StorageStats;
import com.sinai.workflow.model.response.AdminDashboardResponse.UserStats;
import com.sinai.workflow.service.AdminDashboardService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminDashboardController.class, properties = TestJwts.JWT_SECRET_PROPERTY)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, CurrentUserProvider.class})
class AdminDashboardApiWebMvcTest {

    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), "root", Role.ADMIN);
    private static final CurrentUser USER = new CurrentUser(UUID.randomUUID(), "alice", Role.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenStateRepository tokenStateRepository;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @Test
    void anonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(adminDashboardService);
    }

    @Test
    void regularUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard").with(as(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(adminDashboardService);
    }

    @Test
    void adminGetsDashboard() throws Exception {
        when(adminDashboardService.dashboard()).thenReturn(sampleDashboard());

        mockMvc.perform(get("/api/v1/admin/dashboard").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(2))
                .andExpect(jsonPath("$.jobs.byStatus[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.storage.uploadedBytes").value(1024))
                .andExpect(jsonPath("$.dailyJobs[0].date").value("2026-10-25"))
                .andExpect(jsonPath("$.recentJobs[0].ownerUsername").isEmpty())
                .andExpect(jsonPath("$.recentUsers[0].username").value("alice"));
    }

    private static AdminDashboardResponse sampleDashboard() {
        var now = Instant.parse("2026-10-25T00:00:00Z");
        return new AdminDashboardResponse(now,
                new UserStats(2, 2, 1, 1),
                new JobStats(1, List.of(new StatusCount(JobStatus.COMPLETED, 1)), 1),
                new StorageStats(1, 1024),
                List.of(new DailyCount(LocalDate.parse("2026-10-25"), 1)),
                List.of(new RecentJob(UUID.randomUUID(), "clip", JobStatus.COMPLETED, null, now)),
                List.of(new RecentUser(USER.id(), USER.username(), Role.USER, true, now)));
    }
}
