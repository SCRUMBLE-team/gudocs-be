package com.scrumble.gudocs.ocr.dto.response;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record OcrSubscriptionResult(
        @Schema(description = "인식된 서비스명(매칭 실패 시 OCR 원문 best-effort 또는 null)", example = "넷플릭스")
        String serviceName,

        @Schema(description = "카탈로그 서비스 코드. 구독 등록 시 그대로 전달하면 로고가 연결된다. "
                + "카탈로그 매칭에 실패하면 null.", example = "NETFLIX")
        String serviceCode,

        @Schema(description = "카테고리(서비스명 매칭 성공 시에만 채워짐)", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "결제 금액(원)", example = "17000")
        Long price,

        @Schema(description = "요금제명(인식한 금액이 카탈로그 요금제와 정확히 일치할 때만 채워짐)", example = "프리미엄")
        String planName,

        @Schema(description = "결제 주기(기본값 MONTHLY)", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "최초 결제일(연도 정보가 없으면 현재 연도로 추정)", example = "2026-07-15")
        LocalDate firstBillingDate
) {
}
