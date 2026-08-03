package com.scrumble.gudocs.notification.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.notification.dto.response.PushTestResponse;
import com.scrumble.gudocs.notification.service.PushTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 푸시 발송(진단용). 현재 로그인 사용자의 활성 기기에 즉시 푸시를 발송하고
 * 기기별 결과를 돌려준다. setFid 발송이 실기기에 실제 도달하는지 확인하는 용도.
 */
@RestController
@RequestMapping("/api/push-registrations/test")
@RequiredArgsConstructor
public class PushTestController {

    private final PushTestService pushTestService;

    @PostMapping
    public ResponseEntity<ApiResponse<PushTestResponse>> sendTest(@CurrentUserId Long userId) {
        return ResponseEntity.ok(
                ApiResponse.success("테스트 푸시를 발송했습니다.", pushTestService.sendTest(userId)));
    }
}
