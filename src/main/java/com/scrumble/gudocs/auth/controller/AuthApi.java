package com.scrumble.gudocs.auth.controller;

import com.scrumble.gudocs.auth.dto.UserResponse;
import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "인증 API (소셜 로그인, 로그아웃, 내 정보)")
public interface AuthApi {

    @Operation(summary = "로그아웃", description = "현재 세션을 무효화합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @SecurityRequirement(name = "cookieAuth")
    ResponseEntity<ApiResponse<Void>> logout(@Parameter(hidden = true) HttpServletRequest httpRequest);

    @Operation(summary = "내 정보 조회 (Auth)", description = "현재 로그인한 사용자의 기본 정보를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @SecurityRequirement(name = "cookieAuth")
    ResponseEntity<ApiResponse<UserResponse>> me(@Parameter(hidden = true) @CurrentUserId Long userId);
}
