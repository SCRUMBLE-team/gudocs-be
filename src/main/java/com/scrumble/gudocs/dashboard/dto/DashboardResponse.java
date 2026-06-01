package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;

import java.util.List;

public record DashboardResponse(
        Long monthlyTotalExpense,
        int activeSubscriptionCount,
        List<SubscriptionResponse> recentSubscriptions,
        List<CategorySummary> categorySummaries
) {
}
