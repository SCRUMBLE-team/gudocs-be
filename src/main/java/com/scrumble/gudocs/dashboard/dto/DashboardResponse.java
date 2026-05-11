package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.dto.SubscriptionResponse;

import java.util.List;

public record DashboardResponse(
        List<UpcomingNotification> upcomingNotifications,
        Long monthlyTotalExpense,
        int activeSubscriptionCount,
        List<SubscriptionResponse> recentSubscriptions,
        List<CategorySummary> categorySummaries
) {
}
