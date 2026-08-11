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

    /**
     * 구독 등록정보상 해당 월에 도래하는 청구 예정액. 카드·은행의 실제 승인 금액이 아니다.
     */
    public static long scheduledBillingAmount(Subscription subscription, YearMonth target) {
        if (subscription.getBillingCycle() == BillingCycle.MONTHLY) {
            return subscription.getPrice();
        }
        boolean billedThisMonth = subscription.getFirstBillingDate().getMonthValue() == target.getMonthValue();
        return billedThisMonth ? subscription.getPrice() : 0L;
    }
}
