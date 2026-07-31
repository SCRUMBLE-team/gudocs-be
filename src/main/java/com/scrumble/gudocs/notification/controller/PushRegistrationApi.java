package com.scrumble.gudocs.notification.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.dto.response.PushRegistrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Push Registrations", description = "웹 푸시(FCM) 기기 등록 API")
@SecurityRequirement(name = "cookieAuth")
public interface PushRegistrationApi {

    @Operation(summary = "푸시 기기 등록(upsert)",
            description = "현재 로그인 사용자 기준으로 FID를 등록한다. 동일 FID 재등록 시 새 행을 만들지 않고 갱신하며 enabled=true로 만든다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "fid 누락/공백"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<PushRegistrationResponse>> register(
            @CurrentUserId Long userId,
            @RequestBody PushRegistrationRequest request);

    @Operation(summary = "푸시 기기 등록 해제",
            description = "현재 로그인 사용자 소유의 등록을 비활성화(enabled=false)한다. 이미 비활성이어도 성공(멱등).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "다른 사용자 등록 접근"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "등록 정보 없음")
    })
    ResponseEntity<ApiResponse<Void>> unregister(
            @CurrentUserId Long userId,
            Long registrationId);
}
