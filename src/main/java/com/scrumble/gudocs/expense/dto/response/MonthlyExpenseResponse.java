package com.scrumble.gudocs.expense.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record MonthlyExpenseResponse(
        @Schema(description = "조회 연도", example = "2026")
        int year,

        @Schema(description = "조회 월", example = "7")
        int month,

        @Schema(description = "해당 월 총 지출(원)", example = "80000")
        long totalAmount,

        @Schema(description = "전월 총 지출(원)", example = "75000")
        long previousMonthAmount,

        @Schema(description = "전월 대비 증감액(원)", example = "5000")
        long changeAmount,

        @Schema(description = "전월 대비 증감률(%)", example = "6.67")
        double changeRate,

        @Schema(description = "월간 구독 지출 합계(원)", example = "50000")
        long monthlySubscriptionAmount,

        @Schema(description = "연간 구독의 월 환산 합계(원)", example = "30000")
        long annualSubscriptionMonthlyConvertedAmount,

        @JsonProperty("actualAmount")
        @Schema(description = "조회 월에 청구 예정일이 도래해 기록된 금액(원). " +
                "Subscription 등록정보 기반이며 카드·은행의 실제 승인 금액이 아님. " +
                "JSON 필드명 actualAmount는 기존 프론트 호환을 위해 유지", example = "137000")
        long recordedBillingAmount
) {
}
