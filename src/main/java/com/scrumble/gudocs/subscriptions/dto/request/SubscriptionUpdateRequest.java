package com.scrumble.gudocs.subscriptions.dto.request;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

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

        @Schema(description = "최초 결제일(다음 결제일 계산의 기준 앵커)", example = "2026-07-15")
        @NotNull(message = "최초 결제일은 필수입니다.")
        LocalDate firstBillingDate
) {
}
