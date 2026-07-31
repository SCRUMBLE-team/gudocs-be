package com.scrumble.gudocs.notification.util;

import com.scrumble.gudocs.notification.service.DueBilling;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillingReminderCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11); // 월요일

    private Subscription monthly(String name, int billingDay) {
        return Subscription.builder()
                .serviceName(name)
                .category(SubscriptionCategory.OTT)
                .price(17000L)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.of(2025, 1, billingDay))
                .build();
    }

    @Test
    void 오늘부터_7일_이내_결제_대상만_탐색() {
        // billingDay=18 → 05-18 (7일 후, 경계 포함)
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("Netflix", 18)), TODAY, 7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysUntil()).isEqualTo(7);
        assertThat(result.get(0).targetDate()).isEqualTo(LocalDate.of(2026, 5, 18));
    }

    @Test
    void 당일_결제_포함() {
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("Netflix", 11)), TODAY, 7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysUntil()).isZero();
    }

    @Test
    void 여드레_후_결제는_제외() {
        // billingDay=19 → 05-19 (8일 후)
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("Netflix", 19)), TODAY, 7);

        assertThat(result).isEmpty();
    }

    @Test
    void 결제일_오름차순_정렬() {
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("Netflix", 15), monthly("Spotify", 13)), TODAY, 7);

        assertThat(result).extracting(d -> d.subscription().getServiceName())
                .containsExactly("Spotify", "Netflix");
    }
}
