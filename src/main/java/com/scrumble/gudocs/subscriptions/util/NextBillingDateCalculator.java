package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;

import java.time.LocalDate;
import java.time.YearMonth;

public final class NextBillingDateCalculator {

    private NextBillingDateCalculator() {
    }

    public static LocalDate calculate(Subscription subscription, LocalDate today) {
        if (subscription.getBillingCycle() == BillingCycle.MONTHLY) {
            return monthly(subscription.getBillingDay(), today);
        }
        return yearly(subscription.getBillingDay(), subscription.getBillingMonth(), today);
    }

    private static LocalDate monthly(int billingDay, LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate billingDate = currentMonth.atDay(Math.min(billingDay, currentMonth.lengthOfMonth()));

        if (!billingDate.isBefore(today)) {
            return billingDate;
        }

        YearMonth nextMonth = currentMonth.plusMonths(1);
        return nextMonth.atDay(Math.min(billingDay, nextMonth.lengthOfMonth()));
    }

    private static LocalDate yearly(int billingDay, int billingMonth, LocalDate today) {
        YearMonth thisYearMonth = YearMonth.of(today.getYear(), billingMonth);
        LocalDate billingDate = thisYearMonth.atDay(Math.min(billingDay, thisYearMonth.lengthOfMonth()));

        if (!billingDate.isBefore(today)) {
            return billingDate;
        }

        YearMonth nextYearMonth = YearMonth.of(today.getYear() + 1, billingMonth);
        return nextYearMonth.atDay(Math.min(billingDay, nextYearMonth.lengthOfMonth()));
    }
}
