package com.scrumble.gudocs.notification.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.notification.dto.response.UpcomingNotification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

@Tag(name = "Notifications", description = "결제 예정 알림 API")
@SecurityRequirement(name = "cookieAuth")
public interface NotificationApi {

    @Operation(summary = "다가오는 결제 알림 조회",
            description = "7일 이내 결제 예정인 ACTIVE 구독 목록을 결제일 오름차순으로 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<ApiResponse<List<UpcomingNotification>>> getUpcoming(
            @Parameter(hidden = true) UserDetails userDetails);
}
