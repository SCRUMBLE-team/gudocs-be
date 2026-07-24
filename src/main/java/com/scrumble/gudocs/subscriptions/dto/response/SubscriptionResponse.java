package com.scrumble.gudocs.subscriptions.dto.response;

import com.scrumble.gudocs.subscriptions.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        @Schema(description = "구독 ID", example = "1")
        Long id,

        @Schema(description = "서비스명", example = "Netflix")
        String serviceName,

        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "결제 금액(원)", example = "17000")
        Long price,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "결제일(1~31)", example = "15")
        Integer billingDay,

        @Schema(description = "결제 월(1~12, YEARLY 전용)", example = "1", nullable = true)
        Integer billingMonth,

        @Schema(description = "결제 수단", example = "CARD")
        PaymentMethod paymentMethod,

        @Schema(description = "구독 상태", example = "ACTIVE")
        SubscriptionStatus status,

        @Schema(description = "다음 결제일", example = "2026-07-31")
        LocalDate nextBillingDate,

        @Schema(description = "생성 일시", example = "2026-07-01T12:00:00")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-07-10T12:00:00")
        LocalDateTime updatedAt
) {
    public static SubscriptionResponse from(Subscription subscription, LocalDate nextBillingDate) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getServiceName(),
                subscription.getCategory(),
                subscription.getPrice(),
                subscription.getBillingCycle(),
                subscription.getBillingDay(),
                subscription.getBillingMonth(),
                subscription.getPaymentMethod(),
                subscription.getStatus(),
                nextBillingDate,
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
