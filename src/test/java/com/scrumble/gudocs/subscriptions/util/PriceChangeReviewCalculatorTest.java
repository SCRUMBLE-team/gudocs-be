package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog.PriceChange;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PriceChangeReviewCalculatorTest {

    private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 9, 1);
    private static final PriceChange CHANGE = new PriceChange(
            17000L, 19000L, EFFECTIVE_ON, LocalDate.of(2026, 8, 5),
            "https://help.netflix.com/ko/node/example");

    private Subscription subscription(LocalDate firstBillingDate, SubscriptionStatus status) {
        return Subscription.builder()
                .serviceName("넷플릭스")
                .serviceCode("NETFLIX")
                .category(SubscriptionCategory.OTT)
                .price(17000L)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(firstBillingDate)
                .status(status)
                .build();
    }

    @Test
    void 적용일_이후_첫_예정_결제일이_지나야_배너가_필요하다() {
        Subscription subscription = subscription(LocalDate.of(2026, 5, 5), SubscriptionStatus.ACTIVE);

        assertThat(PriceChangeReviewCalculator.isRequired(
                subscription, CHANGE, LocalDate.of(2026, 9, 4))).isFalse();
        assertThat(PriceChangeReviewCalculator.isRequired(
                subscription, CHANGE, LocalDate.of(2026, 9, 5))).isFalse();
        assertThat(PriceChangeReviewCalculator.isRequired(
                subscription, CHANGE, LocalDate.of(2026, 9, 6))).isTrue();
    }

    @Test
    void 적용일_당일에는_배너를_띄우지_않는다() {
        Subscription subscription = subscription(EFFECTIVE_ON, SubscriptionStatus.ACTIVE);

        assertThat(PriceChangeReviewCalculator.isRequired(subscription, CHANGE, EFFECTIVE_ON)).isFalse();
    }

    @Test
    void 일시정지_구독은_예정_결제일이_지나도_배너를_띄우지_않는다() {
        Subscription subscription = subscription(LocalDate.of(2026, 5, 5), SubscriptionStatus.PAUSED);

        assertThat(PriceChangeReviewCalculator.isRequired(
                subscription, CHANGE, LocalDate.of(2026, 9, 6))).isFalse();
    }
}
