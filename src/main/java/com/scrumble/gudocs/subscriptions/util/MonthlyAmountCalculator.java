package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;

public final class MonthlyAmountCalculator {

    private MonthlyAmountCalculator() {
    }

    public static long monthlyAmount(Subscription subscription) {
        return subscription.getBillingCycle() == BillingCycle.MONTHLY
                ? subscription.getPrice()
                : subscription.getPrice() / 12;
    }
}
