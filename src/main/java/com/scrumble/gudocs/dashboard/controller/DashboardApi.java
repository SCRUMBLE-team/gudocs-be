package com.scrumble.gudocs.dashboard.controller;

import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

@Tag(name = "Dashboard", description = "메인 대시보드 API")
@SecurityRequirement(name = "cookieAuth")
public interface DashboardApi {

    @Operation(summary = "대시보드 조회", description = "이번 달 총 지출, 카테고리별 요약, 결제 예정 알림 등 메인 대시보드 데이터를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(@Parameter(hidden = true) UserDetails userDetails);
}
