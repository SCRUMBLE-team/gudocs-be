package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.util.MonthlyAmountCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

public record DuplicateSubscriptionItem(
        @Schema(description = "구독 ID", example = "1")
        Long id,

        @Schema(description = "서비스명", example = "Netflix")
        String serviceName,

        @Schema(description = "결제 금액(원)", example = "17000")
        Long price,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "월 환산 금액(원)", example = "17000")
        Long monthlyAmount
) {
    public static DuplicateSubscriptionItem from(Subscription subscription) {
        return new DuplicateSubscriptionItem(
                subscription.getId(),
                subscription.getServiceName(),
                subscription.getPrice(),
                subscription.getBillingCycle(),
                MonthlyAmountCalculator.monthlyAmount(subscription)
        );
    }
}
