package com.scrumble.gudocs.users.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.users.dto.UserInfoResponse;
import com.scrumble.gudocs.users.dto.UserNameUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Users", description = "마이페이지 API (내 정보 조회/수정, 회원 탈퇴)")
@SecurityRequirement(name = "cookieAuth")
public interface UserApi {

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<ApiResponse<UserInfoResponse>> getMyInfo(@Parameter(hidden = true) @CurrentUserId Long userId);

    @Operation(summary = "이름 수정", description = "현재 로그인한 사용자의 이름을 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이름 누락 또는 공백"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<UserInfoResponse>> updateName(@Parameter(hidden = true) @CurrentUserId Long userId,
            UserNameUpdateRequest request);

    @Operation(summary = "회원 탈퇴", description = "세션 인증으로 본인 확인 후 계정 및 구독 정보를 삭제합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<Void>> deleteAccount(@Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(hidden = true) HttpServletRequest httpRequest);
}
