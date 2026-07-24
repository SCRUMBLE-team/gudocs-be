package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DashboardResponse(
        @Schema(description = "이번 달 총 지출(원)", example = "58000")
        Long monthlyTotalExpense,

        @Schema(description = "활성 구독 수", example = "5")
        int activeSubscriptionCount,

        @Schema(description = "최근 등록 구독 목록")
        List<SubscriptionResponse> recentSubscriptions,

        @Schema(description = "카테고리별 요약")
        List<CategorySummary> categorySummaries
) {
}
