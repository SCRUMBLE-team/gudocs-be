package com.scrumble.gudocs.expense.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record MonthlyTrendItem(
        @Schema(description = "연도", example = "2026")
        int year,

        @Schema(description = "월", example = "7")
        int month,

        @Schema(description = "해당 월 총 지출(원)", example = "80000")
        long totalAmount
) {
}
