package com.scrumble.gudocs.notification.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.notification.dto.request.NotificationPreviewRequest;
import com.scrumble.gudocs.notification.dto.response.NotificationPreviewResponse;
import com.scrumble.gudocs.notification.dto.response.PushTestResponse;
import com.scrumble.gudocs.notification.service.NotificationPreviewService;
import com.scrumble.gudocs.notification.service.PushTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final NotificationPreviewService notificationPreviewService;

    @Operation(summary = "테스트 푸시 발송(진단용)",
            description = "현재 로그인 사용자의 활성 기기에 즉시 테스트 푸시를 발송하고 기기별 결과를 반환한다. "
                    + "응답 senderType이 FcmPushSender이고 result가 SUCCESS면 setFid 발송 정상. "
                    + "NoopPushSender면 Firebase 비활성 환경이라 실발송이 아니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PushTestResponse>> sendTest(@CurrentUserId Long userId) {
        return ResponseEntity.ok(
                ApiResponse.success("테스트 푸시를 발송했습니다.", pushTestService.sendTest(userId)));
    }

    @Operation(summary = "알림 미리보기 발송(시연·검증용)",
            description = """
                    결제 예정·해지 알림을 지금 즉시 발송한다. 스케줄러 cron·D-3 판정·중복 방지를 기다리지 \
                    않으므로 원하는 순간에, **몇 번이든 반복해서** 띄울 수 있다.

                    위의 테스트 푸시와 달리 실제 배치와 **같은 문구 생성 코드**를 재사용하므로 제목·본문·이동 \
                    링크가 실제 알림과 동일하다. 응답에도 발송된 문구와 link를 함께 담는다.

                    발송 이력(user_notifications)을 남기지 않는다 — 남기면 그날 진짜 알림이 중복으로 \
                    간주돼 발송되지 않는다. 따라서 이 API로는 중복 방지·발송 단계 판정이 검증되지 않는다.

                    본인 구독만 지정할 수 있다(403). 종류별 입력이 다르다:
                    - `BILLING_REMINDER`·`CANCEL_REMINDER`: `daysUntil` 필수(3 또는 0)
                    - `PRICE_CHANGE`: `newPrice`·`effectiveOn` 필수, `sourceUrl` 선택(생략 시 카탈로그 링크)

                    **가격 변경만 성격이 다르다.** 결제·해지는 구독 정보만으로 문구가 완성돼 실제로 받게 될 \
                    알림과 동일하지만, 가격 변경의 내용은 원래 카탈로그에 사람이 공식 발표를 확인해 등록한 \
                    값에서 온다. 미리보기는 요청으로 받은 값으로 대신 만들므로 **형식 미리보기**이고, \
                    거기 실린 인상·인하 내용은 검증된 공식 발표가 아니다.""")
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<NotificationPreviewResponse>> preview(
            @CurrentUserId Long userId,
            @Valid @RequestBody NotificationPreviewRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("알림 미리보기를 발송했습니다.", notificationPreviewService.send(userId, request)));
    }
}
