package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

public record CategorySummary(
        SubscriptionCategory category,
        Long monthlyAmount,
        double ratio
) {
}
