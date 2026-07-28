package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.util.MonthlyAmountCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record UnusedSubscriptionCandidate(
        @Schema(description = "구독 ID", example = "3")
        Long id,

        @Schema(description = "서비스명", example = "Adobe CC")
        String serviceName,

        @Schema(description = "카테고리", example = "DESIGN")
        SubscriptionCategory category,

        @Schema(description = "결제 금액(원)", example = "24000")
        Long price,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "월 환산 금액(원)", example = "24000")
        Long monthlyAmount,

        @Schema(description = "마지막 수정 일시", example = "2026-01-10T09:00:00")
        LocalDateTime updatedAt
) {
    public static UnusedSubscriptionCandidate from(Subscription subscription) {
        return new UnusedSubscriptionCandidate(
                subscription.getId(),
                subscription.getServiceName(),
                subscription.getCategory(),
                subscription.getPrice(),
                subscription.getBillingCycle(),
                MonthlyAmountCalculator.monthlyAmount(subscription),
                subscription.getUpdatedAt()
        );
    }
}
