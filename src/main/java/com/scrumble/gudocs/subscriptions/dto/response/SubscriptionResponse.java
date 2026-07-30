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

        @Schema(description = "최초 결제일(다음 결제일 계산의 기준 앵커)", example = "2026-07-15")
        LocalDate firstBillingDate,

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
                subscription.getFirstBillingDate(),
                subscription.getStatus(),
                nextBillingDate,
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
