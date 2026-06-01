package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.notification.dto.response.UpcomingNotification;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;

import java.util.List;

public record DashboardResponse(
        List<UpcomingNotification> upcomingNotifications,
        Long monthlyTotalExpense,
        int activeSubscriptionCount,
        List<SubscriptionResponse> recentSubscriptions,
        List<CategorySummary> categorySummaries
) {
}
