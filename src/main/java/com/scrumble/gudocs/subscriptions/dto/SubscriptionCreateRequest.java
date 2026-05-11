package com.scrumble.gudocs.subscriptions.dto;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscriptionCreateRequest(
        @NotBlank(message = "서비스명은 필수입니다.")
        String serviceName,

        @NotNull(message = "카테고리는 필수입니다.")
        SubscriptionCategory category,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
        Long price,

        @NotNull(message = "결제 주기는 필수입니다.")
        BillingCycle billingCycle,

        @NotNull(message = "결제일은 필수입니다.")
        @Min(value = 1, message = "결제일은 1~31 사이여야 합니다.")
        @Max(value = 31, message = "결제일은 1~31 사이여야 합니다.")
        Integer billingDay,

        @Min(value = 1, message = "결제 월은 1~12 사이여야 합니다.")
        @Max(value = 12, message = "결제 월은 1~12 사이여야 합니다.")
        Integer billingMonth,

        @NotNull(message = "결제 수단은 필수입니다.")
        PaymentMethod paymentMethod
) {
}
