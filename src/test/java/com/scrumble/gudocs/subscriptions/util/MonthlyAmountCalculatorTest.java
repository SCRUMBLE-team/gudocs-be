package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.common.fixture.UserFixture;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.users.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyAmountCalculatorTest {

    private final User user = UserFixture.create();

    private Subscription monthly(long price, int billingDay) {
        return Subscription.builder()
                .user(user)
                .serviceName("Netflix")
                .category(SubscriptionCategory.OTT)
                .price(price)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.of(2025, 1, billingDay))
                
                .build();
    }

    private Subscription yearly(long price, int billingMonth, int billingDay) {
        return Subscription.builder()
                .user(user)
                .serviceName("Adobe")
                .category(SubscriptionCategory.DESIGN)
                .price(price)
                .billingCycle(BillingCycle.YEARLY)
                .firstBillingDate(LocalDate.of(2025, billingMonth, billingDay))
                
                .build();
    }

    @Test
    void MONTHLY_구독은_price_전액() {
        Subscription s = monthly(17000L, 15);

        assertThat(MonthlyAmountCalculator.monthlyAmount(s)).isEqualTo(17000L);
    }

    @Test
    void YEARLY_구독은_price를_12로_나눈_값_버림() {
        Subscription s = yearly(100000L, 3, 1);

        assertThat(MonthlyAmountCalculator.monthlyAmount(s)).isEqualTo(8333L);
    }

    @Test
    void MONTHLY_구독은_조회월과_무관하게_항상_price_전액() {
        Subscription s = monthly(17000L, 15);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 1)))
                .isEqualTo(17000L);
        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 7)))
                .isEqualTo(17000L);
    }

    @Test
    void YEARLY_구독은_결제월과_조회월이_같으면_price_전액() {
        Subscription s = yearly(120000L, 3, 1);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 3)))
                .isEqualTo(120000L);
    }

    @Test
    void YEARLY_구독은_결제월과_조회월이_다르면_0원() {
        Subscription s = yearly(120000L, 3, 1);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 4)))
                .isEqualTo(0L);
    }

    @Test
    void YEARLY_구독은_연도가_달라도_월만_같으면_price_전액() {
        Subscription s = yearly(120000L, 3, 1);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2030, 3)))
                .isEqualTo(120000L);
    }
}
