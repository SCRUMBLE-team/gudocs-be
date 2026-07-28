package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;

import java.time.YearMonth;

public final class MonthlyAmountCalculator {

    private MonthlyAmountCalculator() {
    }

    public static long monthlyAmount(Subscription subscription) {
        return subscription.getBillingCycle() == BillingCycle.MONTHLY
                ? subscription.getPrice()
                : subscription.getPrice() / 12;
    }

    public static long actualAmount(Subscription subscription, YearMonth target) {
        if (subscription.getBillingCycle() == BillingCycle.MONTHLY) {
            return subscription.getPrice();
        }
        boolean billedThisMonth = subscription.getFirstBillingDate().getMonthValue() == target.getMonthValue();
        return billedThisMonth ? subscription.getPrice() : 0L;
    }
}
