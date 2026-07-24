package com.scrumble.gudocs.expense.dto.response;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record SubscriptionExpenseDetail(
        @Schema(description = "구독 ID", example = "1")
        Long subscriptionId,

        @Schema(description = "서비스명", example = "Netflix")
        String serviceName,

        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "카테고리 표시명", example = "OTT")
        String categoryName,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "원래 결제 금액(원)", example = "17000")
        long originalPrice,

        @Schema(description = "해당 월에 적용된 금액(원, YEARLY는 월 환산)", example = "17000")
        long appliedMonthlyAmount,

        @Schema(description = "결제일(1~31)", example = "15")
        Integer billingDay,

        @Schema(description = "결제 월(1~12, YEARLY 전용)", example = "1", nullable = true)
        Integer billingMonth,

        @Schema(description = "결제 수단", example = "CARD")
        PaymentMethod paymentMethod,

        @Schema(description = "구독 상태", example = "ACTIVE")
        SubscriptionStatus status,

        @Schema(description = "삭제 여부(soft delete)", example = "false")
        boolean deleted
) {
}
