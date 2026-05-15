package com.scrumble.gudocs.subscriptions.dto.request;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SubscriptionUpdateRequest(
        @NotBlank(message = "서비스명은 공백일 수 없습니다.")
        String serviceName,

        SubscriptionCategory category,

        @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
        @Max(value = 10_000_000, message = "결제 금액은 10,000,000원 이하여야 합니다.")
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
