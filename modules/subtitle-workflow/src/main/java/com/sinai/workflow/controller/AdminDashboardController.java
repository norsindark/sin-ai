package com.sinai.workflow.controller;

import com.sinai.workflow.api.AdminDashboardApi;
import com.sinai.workflow.model.response.AdminDashboardResponse;
import com.sinai.workflow.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminDashboardController implements AdminDashboardApi {

    private final AdminDashboardService adminDashboardService;

    @Override
    public AdminDashboardResponse dashboard() {
        return adminDashboardService.dashboard();
    }
}
