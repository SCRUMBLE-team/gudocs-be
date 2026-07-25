package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;

import java.time.LocalDate;
import java.time.YearMonth;

public final class NextBillingDateCalculator {

    private NextBillingDateCalculator() {
    }

    public static LocalDate calculate(Subscription subscription, LocalDate today) {
        LocalDate anchor = subscription.getFirstBillingDate();

        // 최초 결제일이 아직 도래하지 않았으면 그 날이 다음 결제일
        if (!anchor.isBefore(today)) {
            return anchor;
        }

        return subscription.getBillingCycle() == BillingCycle.MONTHLY
                ? nextMonthly(anchor, today)
                : nextYearly(anchor, today);
    }

    private static LocalDate nextMonthly(LocalDate anchor, LocalDate today) {
        int day = anchor.getDayOfMonth();
        YearMonth month = YearMonth.from(today);
        LocalDate billingDate = month.atDay(Math.min(day, month.lengthOfMonth()));

        if (billingDate.isBefore(today)) {
            month = month.plusMonths(1);
            billingDate = month.atDay(Math.min(day, month.lengthOfMonth()));
        }
        return billingDate;
    }

    private static LocalDate nextYearly(LocalDate anchor, LocalDate today) {
        int day = anchor.getDayOfMonth();
        int monthValue = anchor.getMonthValue();
        YearMonth yearMonth = YearMonth.of(today.getYear(), monthValue);
        LocalDate billingDate = yearMonth.atDay(Math.min(day, yearMonth.lengthOfMonth()));

        if (billingDate.isBefore(today)) {
            yearMonth = YearMonth.of(today.getYear() + 1, monthValue);
            billingDate = yearMonth.atDay(Math.min(day, yearMonth.lengthOfMonth()));
        }
        return billingDate;
    }
}
