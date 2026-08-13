package com.scrumble.gudocs.notification.dto.request;

import com.scrumble.gudocs.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * 알림 미리보기 요청. 스케줄러가 고르는 대상 조건(D-3·당일, 절약 후보 체크, 카탈로그 선언)을
 * 기다리지 않고 원하는 알림을 즉시 띄우기 위한 입력이다.
 *
 * <p>필요한 값이 알림 종류마다 다르다. 그래서 {@code type}·{@code subscriptionId} 외에는
 * Bean Validation 을 걸지 않고 {@code NotificationPreviewService} 가 종류별로 검사한다 —
 * 한쪽에 필수인 값이 다른 쪽에는 의미가 없어서, 애너테이션만으로는 "PRICE_CHANGE 인데
 * daysUntil 이 필수"처럼 틀린 요구를 하게 된다.
 *
 * @param type           BILLING_REMINDER · CANCEL_REMINDER · PRICE_CHANGE. 그 외는 400
 * @param subscriptionId 알림에 실을 구독. 본인 구독이 아니면 403
 * @param daysUntil      <b>결제 예정·해지 알림 전용(필수)</b>. 3(D-3) 또는 0(당일)
 * @param newPrice       <b>가격 변경 전용(필수)</b>. 변경 후 금액. 구독의 현재 가격이 변경 전 금액이 된다
 * @param effectiveOn    <b>가격 변경 전용(필수)</b>. 변경 적용 예정일
 * @param sourceUrl      <b>가격 변경 전용(선택)</b>. 알림을 눌렀을 때 열 공식 안내 URL.
 *                       생략하면 카탈로그의 서비스 링크를 쓴다
 */
public record NotificationPreviewRequest(
        @Schema(description = "알림 종류", example = "BILLING_REMINDER",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "알림 종류는 필수입니다.")
        NotificationType type,

        @Schema(description = "알림에 실을 구독 ID(본인 구독만)", example = "45",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "구독 ID는 필수입니다.")
        Long subscriptionId,

        @Schema(description = "결제 며칠 전 상황으로 보낼지. 3 또는 0. "
                + "BILLING_REMINDER·CANCEL_REMINDER에서만 쓰이며 그때는 필수", example = "3")
        Integer daysUntil,

        @Schema(description = "변경 후 금액(원). PRICE_CHANGE 전용 필수. "
                + "구독의 현재 가격보다 크면 '인상', 작으면 '인하' 문구가 나간다", example = "31900")
        @Positive(message = "변경 후 금액은 0보다 커야 합니다.")
        Long newPrice,

        @Schema(description = "변경 적용 예정일. PRICE_CHANGE 전용 필수", example = "2026-09-01")
        LocalDate effectiveOn,

        @Schema(description = "알림을 눌렀을 때 열 공식 안내 URL. PRICE_CHANGE 전용 선택 — "
                + "생략하면 카탈로그의 서비스 링크를 쓴다", example = "https://claude.ai/settings/billing")
        String sourceUrl
) {
}
