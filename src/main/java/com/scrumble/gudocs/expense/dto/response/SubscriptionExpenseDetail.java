package com.scrumble.gudocs.expense.dto.response;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

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

        @Schema(description = "최초 결제일(다음 결제일 계산의 기준 앵커)", example = "2026-07-15")
        LocalDate firstBillingDate,

        @Schema(description = "구독 상태", example = "ACTIVE")
        SubscriptionStatus status,

        @Schema(description = "삭제 여부(soft delete)", example = "false")
        boolean deleted
) {
}
