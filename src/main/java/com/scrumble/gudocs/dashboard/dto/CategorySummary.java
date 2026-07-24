package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategorySummary(
        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "월 환산 금액(원)", example = "34000")
        Long monthlyAmount,

        @Schema(description = "전체 대비 비율(%)", example = "42.5")
        double ratio
) {
}
