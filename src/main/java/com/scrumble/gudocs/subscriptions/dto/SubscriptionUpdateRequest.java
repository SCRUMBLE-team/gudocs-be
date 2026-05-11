package com.scrumble.gudocs.subscriptions.dto;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SubscriptionUpdateRequest(
        String serviceName,
        SubscriptionCategory category,

        @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
        Long price,

        BillingCycle billingCycle,

        @Min(value = 1, message = "결제일은 1~31 사이여야 합니다.")
        @Max(value = 31, message = "결제일은 1~31 사이여야 합니다.")
        Integer billingDay,

        @Min(value = 1, message = "결제 월은 1~12 사이여야 합니다.")
        @Max(value = 12, message = "결제 월은 1~12 사이여야 합니다.")
        Integer billingMonth,

        PaymentMethod paymentMethod
) {
}
