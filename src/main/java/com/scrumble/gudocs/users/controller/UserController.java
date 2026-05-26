package com.scrumble.gudocs.users.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.users.dto.UserDeleteRequest;
import com.scrumble.gudocs.users.dto.UserInfoResponse;
import com.scrumble.gudocs.users.dto.UserNameUpdateRequest;
import com.scrumble.gudocs.users.dto.UserPasswordUpdateRequest;
import com.scrumble.gudocs.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "마이페이지 API (내 정보 조회/수정, 회원 탈퇴)")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getMyInfo(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserInfoResponse response = userService.getMyInfo(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("내 정보 조회에 성공했습니다.", response));
    }

    @Operation(summary = "이름 수정", description = "현재 로그인한 사용자의 이름을 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이름 누락 또는 공백"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @PutMapping("/me/name")
    public ResponseEntity<ApiResponse<UserInfoResponse>> updateName(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserNameUpdateRequest request) {
        UserInfoResponse response = userService.updateName(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success("이름이 수정되었습니다.", response));
    }

    @Operation(summary = "비밀번호 수정", description = "현재 비밀번호 확인 후 새 비밀번호로 변경합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치 또는 새 비밀번호 정책 위반"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserPasswordUpdateRequest request) {
        userService.updatePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 수정되었습니다."));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 비밀번호로 본인 확인 후 계정 및 구독 정보를 삭제합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "비밀번호 불일치"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserDeleteRequest request,
            HttpServletRequest httpRequest) {
        userService.deleteAccount(userDetails.getUsername(), request);
        var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다."));
    }
}
