package com.scrumble.gudocs.subscriptions.dto;

import com.scrumble.gudocs.subscriptions.entity.*;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long id,
        String serviceName,
        SubscriptionCategory category,
        Long price,
        BillingCycle billingCycle,
        Integer billingDay,
        Integer billingMonth,
        PaymentMethod paymentMethod,
        SubscriptionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SubscriptionResponse from(Subscription subscription) {
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
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
