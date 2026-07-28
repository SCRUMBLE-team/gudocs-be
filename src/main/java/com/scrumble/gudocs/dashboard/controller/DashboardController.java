package com.scrumble.gudocs.dashboard.controller;

import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.dashboard.dto.InspectionResponse;
import com.scrumble.gudocs.dashboard.service.DashboardService;
import com.scrumble.gudocs.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.scrumble.gudocs.global.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @CurrentUserId Long userId) {
        DashboardResponse response = dashboardService.getDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success("대시보드 조회 성공", response));
    }

    @Override
    @GetMapping("/inspection")
    public ResponseEntity<ApiResponse<InspectionResponse>> getInspection(
            @CurrentUserId Long userId) {
        InspectionResponse response = dashboardService.getInspection(userId);
        return ResponseEntity.ok(ApiResponse.success("구독 점검에 성공했습니다.", response));
    }
}
