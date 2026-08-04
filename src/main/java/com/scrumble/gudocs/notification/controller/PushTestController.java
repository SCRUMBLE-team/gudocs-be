package com.scrumble.gudocs.notification.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.notification.dto.response.PushTestResponse;
import com.scrumble.gudocs.notification.service.PushTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 푸시 발송(진단용). 현재 로그인 사용자의 활성 기기에 즉시 푸시를 발송하고
 * 기기별 결과를 돌려준다. setFid 발송이 실기기에 실제 도달하는지 확인하는 용도.
 */
@Tag(name = "Push Registrations", description = "웹 푸시(FCM) 기기 등록 API")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/push-registrations/test")
@RequiredArgsConstructor
public class PushTestController {

    private final PushTestService pushTestService;

    @Operation(summary = "테스트 푸시 발송(진단용)",
            description = "현재 로그인 사용자의 활성 기기에 즉시 테스트 푸시를 발송하고 기기별 결과를 반환한다. "
                    + "응답 senderType이 FcmPushSender이고 result가 SUCCESS면 setFid 발송 정상. "
                    + "NoopPushSender면 Firebase 비활성 환경이라 실발송이 아니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PushTestResponse>> sendTest(@CurrentUserId Long userId) {
        return ResponseEntity.ok(
                ApiResponse.success("테스트 푸시를 발송했습니다.", pushTestService.sendTest(userId)));
    }
}
