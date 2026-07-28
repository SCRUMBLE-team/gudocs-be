package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.common.fixture.UserFixture;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.users.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
                .paymentMethod(PaymentMethod.CARD)
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
                .paymentMethod(PaymentMethod.CARD)
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
}
