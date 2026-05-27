package com.scrumble.gudocs.expense.dto.response;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;

public record SubscriptionExpenseDetail(
        Long subscriptionId,
        String serviceName,
        SubscriptionCategory category,
        String categoryName,
        BillingCycle billingCycle,
        long originalPrice,
        long appliedMonthlyAmount,
        Integer billingDay,
        Integer billingMonth,
        PaymentMethod paymentMethod,
        SubscriptionStatus status,
        boolean deleted
) {
}
