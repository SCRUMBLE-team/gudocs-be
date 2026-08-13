package com.scrumble.gudocs.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 알림 미리보기 발송 결과. 기기로 실제 간 문구·이동 링크를 함께 돌려주므로, 푸시를 받지 못하는
 * 환경(NoopPushSender 등)에서도 응답만으로 무엇이 나갔을지 확인할 수 있다.
 *
 * @param type        발송한 알림 종류
 * @param title       실제 발송된 푸시 제목
 * @param body        실제 발송된 푸시 본문
 * @param link        푸시를 눌렀을 때 이동할 절대 URL
 * @param senderType  실제로 동작한 PushSender 구현 이름. NoopPushSender면 실발송이 아니다
 * @param deviceCount 발송을 시도한 활성 기기 수
 * @param results     기기별 발송 결과
 */
public record NotificationPreviewResponse(
        @Schema(description = "발송한 알림 종류", example = "BILLING_REMINDER")
        String type,

        @Schema(description = "실제 발송된 푸시 제목", example = "Claude 결제 예정")
        String title,

        @Schema(description = "실제 발송된 푸시 본문", example = "3일 후 28,400원이 결제될 예정이에요.")
        String body,

        @Schema(description = "푸시를 눌렀을 때 이동할 URL",
                example = "https://gudocs-fe-v2.vercel.app/subscriptions/45")
        String link,

        @Schema(description = "실제로 동작한 PushSender 구현. NoopPushSender면 실발송이 아님",
                example = "FcmPushSender")
        String senderType,

        @Schema(description = "발송을 시도한 활성 기기 수", example = "1")
        int deviceCount,

        @Schema(description = "기기별 발송 결과")
        List<PushTestResponse.DeviceResult> results
) {
}
