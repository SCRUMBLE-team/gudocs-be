package com.scrumble.gudocs.expense.dto.response;

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

        @Schema(description = "이번 달 실제 결제 금액(연간 구독은 결제월에만 전액 반영, 원)", example = "137000")
        long actualAmount
) {
}
