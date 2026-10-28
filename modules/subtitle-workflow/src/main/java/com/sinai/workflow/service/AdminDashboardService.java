package com.sinai.workflow.service;

import com.sinai.common.constant.JobStatus;
import com.sinai.identity.api.UserDirectory;
import com.sinai.workflow.model.response.AdminDashboardResponse;
import com.sinai.workflow.model.response.AdminDashboardResponse.DailyCount;
import com.sinai.workflow.model.response.AdminDashboardResponse.JobStats;
import com.sinai.workflow.model.response.AdminDashboardResponse.RecentJob;
import com.sinai.workflow.model.response.AdminDashboardResponse.RecentUser;
import com.sinai.workflow.model.response.AdminDashboardResponse.StatusCount;
import com.sinai.workflow.model.response.AdminDashboardResponse.StorageStats;
import com.sinai.workflow.model.response.AdminDashboardResponse.UserStats;
import com.sinai.workflow.repository.DashboardRepository;
import com.sinai.workflow.repository.DashboardRepository.RecentJobRow;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    static final int DAILY_JOB_DAYS = 14;
    static final int RECENT_JOBS = 8;
    static final int RECENT_USERS = 5;
    static final Duration NEW_WINDOW = Duration.ofDays(7);

    private final DashboardRepository dashboardRepository;
    private final UserDirectory userDirectory;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var firstDay = today.minusDays(DAILY_JOB_DAYS - 1);
        var newSince = now.minus(NEW_WINDOW);

        var userCounts = userDirectory.countUsers(newSince);
        var jobsByStatus = dashboardRepository.countJobsByStatus();
        var jobsPerDay = dashboardRepository.countJobsPerUtcDay(firstDay.atStartOfDay(ZoneOffset.UTC).toInstant());
        var storedVideos = dashboardRepository.sumStoredVideos();
        var recentJobRows = dashboardRepository.findRecentJobs(RECENT_JOBS);
        var ownerNames = userDirectory.usernamesByIds(recentJobRows.stream()
                .map(RecentJobRow::ownerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        var statusCounts = Arrays.stream(JobStatus.values())
                .map(status -> new StatusCount(status, jobsByStatus.getOrDefault(status, 0L)))
                .toList();
        var dailyJobs = LongStream.range(0, DAILY_JOB_DAYS)
                .mapToObj(firstDay::plusDays)
                .map(day -> new DailyCount(day, jobsPerDay.getOrDefault(day, 0L)))
                .toList();
        var createdLast7Days = dailyJobs.stream()
                .skip(DAILY_JOB_DAYS - NEW_WINDOW.toDays())
                .mapToLong(DailyCount::count)
                .sum();
        var recentJobs = recentJobRows.stream()
                .map(row -> new RecentJob(row.id(), row.title(), row.status(),
                        Objects.isNull(row.ownerId()) ? null : ownerNames.get(row.ownerId()), row.createdAt()))
                .toList();
        var recentUsers = userDirectory.newestUsers(RECENT_USERS).stream()
                .map(user -> new RecentUser(user.id(), user.username(), user.role(), user.enabled(),
                        user.createdAt()))
                .toList();

        return new AdminDashboardResponse(now,
                new UserStats(userCounts.total(), userCounts.enabled(), userCounts.activeAdmins(),
                        userCounts.newSince()),
                new JobStats(statusCounts.stream().mapToLong(StatusCount::count).sum(), statusCounts,
                        createdLast7Days),
                new StorageStats(storedVideos.count(), storedVideos.totalBytes()),
                dailyJobs, recentJobs, recentUsers);
    }
}
