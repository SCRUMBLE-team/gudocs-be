package com.scrumble.gudocs.notification.util;

import com.scrumble.gudocs.notification.service.DueBilling;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BillingReminderCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11); // 월요일
    private static final Set<Integer> OFFSETS = Set.of(3, 0); // D-3, 당일

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
    void D3_결제_대상_탐색() {
        // billingDay=14 → 05-14 (3일 후)
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("Netflix", 14)), TODAY, OFFSETS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysUntil()).isEqualTo(3);
        assertThat(result.get(0).targetDate()).isEqualTo(LocalDate.of(2026, 5, 14));
    }

    @Test
    void 당일_결제_포함() {
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("Netflix", 11)), TODAY, OFFSETS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysUntil()).isZero();
    }

    @Test
    void D1_D2_D4_등_다른_시점은_제외() {
        // 12(D-1), 13(D-2), 15(D-4), 18(D-7) → 모두 제외
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("A", 12), monthly("B", 13), monthly("C", 15), monthly("D", 18)),
                TODAY, OFFSETS);

        assertThat(result).isEmpty();
    }

    @Test
    void 결제일_오름차순_정렬() {
        // 14(D-3), 11(D-0)
        List<DueBilling> result = BillingReminderCalculator.findDue(
                List.of(monthly("Netflix", 14), monthly("Spotify", 11)), TODAY, OFFSETS);

        assertThat(result).extracting(d -> d.subscription().getServiceName())
                .containsExactly("Spotify", "Netflix");
    }
}
