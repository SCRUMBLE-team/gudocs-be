package com.scrumble.gudocs.auth.controller;

import com.scrumble.gudocs.auth.dto.LoginRequest;
import com.scrumble.gudocs.auth.dto.SignupRequest;
import com.scrumble.gudocs.auth.dto.UserResponse;
import com.scrumble.gudocs.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

@Tag(name = "Auth", description = "인증 API (회원가입, 로그인, 로그아웃)")
public interface AuthApi {

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 이름으로 회원가입합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
    })
    ResponseEntity<ApiResponse<Void>> signup(SignupRequest request);

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다. 성공 시 세션 쿠키(JSESSIONID)가 발급됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    })
    ResponseEntity<ApiResponse<Void>> login(LoginRequest request,
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(hidden = true) HttpServletResponse httpResponse);

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
    ResponseEntity<ApiResponse<UserResponse>> me(@Parameter(hidden = true) UserDetails userDetails);
}
