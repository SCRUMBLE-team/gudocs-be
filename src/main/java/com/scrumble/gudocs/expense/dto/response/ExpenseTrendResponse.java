package com.scrumble.gudocs.expense.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ExpenseTrendResponse(
        @Schema(description = "기준 연도", example = "2026")
        int baseYear,

        @Schema(description = "기준 월", example = "7")
        int baseMonth,

        @Schema(description = "월별 지출 추이")
        List<MonthlyTrendItem> monthlyTrends
) {
}
