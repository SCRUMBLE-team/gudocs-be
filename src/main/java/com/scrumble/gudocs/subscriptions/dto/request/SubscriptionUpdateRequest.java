package com.scrumble.gudocs.subscriptions.dto.request;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscriptionUpdateRequest(
        @Schema(description = "서비스명", example = "Netflix")
        @NotBlank(message = "서비스명은 필수입니다.")
        String serviceName,

        @Schema(description = "카테고리", example = "OTT")
        @NotNull(message = "카테고리는 필수입니다.")
        SubscriptionCategory category,

        @Schema(description = "결제 금액(원)", example = "17000")
        @NotNull(message = "결제 금액은 필수입니다.")
        @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
        @Max(value = 10_000_000, message = "결제 금액은 10,000,000원 이하여야 합니다.")
        Long price,

        @Schema(description = "결제 주기", example = "MONTHLY")
        @NotNull(message = "결제 주기는 필수입니다.")
        BillingCycle billingCycle,

        @Schema(description = "결제일(1~31)", example = "15")
        @NotNull(message = "결제일은 필수입니다.")
        @Min(value = 1, message = "결제일은 1~31 사이여야 합니다.")
        @Max(value = 31, message = "결제일은 1~31 사이여야 합니다.")
        Integer billingDay,

        @Schema(description = "결제 월(1~12, YEARLY 전용)", example = "1", nullable = true)
        @Min(value = 1, message = "결제 월은 1~12 사이여야 합니다.")
        @Max(value = 12, message = "결제 월은 1~12 사이여야 합니다.")
        Integer billingMonth,

        @Schema(description = "결제 수단", example = "CARD")
        @NotNull(message = "결제 수단은 필수입니다.")
        PaymentMethod paymentMethod
) {
}
