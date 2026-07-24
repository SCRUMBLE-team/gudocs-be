package com.scrumble.gudocs.expense.dto.response;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryExpenseItem(
        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "카테고리 표시명", example = "OTT")
        String categoryName,

        @Schema(description = "해당 카테고리 지출 금액(원)", example = "34000")
        long amount,

        @Schema(description = "전체 대비 비율(%)", example = "42.5")
        double ratio,

        @Schema(description = "해당 카테고리 구독 수", example = "2")
        int subscriptionCount
) {
}
