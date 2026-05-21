package com.scrumble.gudocs.expense.dto.response;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

public record CategoryExpenseItem(
        SubscriptionCategory category,
        String categoryName,
        long amount,
        double ratio,
        int subscriptionCount
) {
}
