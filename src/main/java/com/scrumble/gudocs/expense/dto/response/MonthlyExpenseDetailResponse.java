package com.scrumble.gudocs.expense.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MonthlyExpenseDetailResponse(
        @Schema(description = "조회 연도", example = "2026")
        int year,

        @Schema(description = "조회 월", example = "7")
        int month,

        @Schema(description = "해당 월 총 지출(원)", example = "80000")
        long totalAmount,

        @Schema(description = "구독별 지출 상세 목록")
        List<SubscriptionExpenseDetail> subscriptions
) {
}
