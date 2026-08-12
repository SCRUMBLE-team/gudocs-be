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

        @Schema(description = "카탈로그 서비스 코드. 프론트는 이 값으로 로고를 찾는다(직접 입력 서비스면 null).",
                example = "NETFLIX")
        String serviceCode,

        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "카테고리 표시명", example = "OTT")
        String categoryName,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "그 달에 기록된 청구 금액(원). 청구 시점의 스냅샷이라 이후 구독 가격을 "
                + "수정해도 바뀌지 않는다. 카드·은행의 실제 승인 금액은 아님", example = "17000")
        long originalPrice,

        @Schema(description = "해당 월에 적용된 금액(원). 연간 구독은 청구액을 커버 기간 12개월로 나눈 값",
                example = "17000")
        long appliedMonthlyAmount,

        @Schema(description = "최초 결제일(다음 결제일 계산의 기준 앵커). 구독의 현재 값이며, "
                + "그 달의 청구일은 billingDate", example = "2026-01-15")
        LocalDate firstBillingDate,

        @Schema(description = "그 달에 청구 예정일이 도래한 날짜. 연간 구독처럼 그 달에 청구가 없고 "
                + "이전 청구가 커버 중이면 그 청구일(과거 날짜)이 들어간다", example = "2026-07-15")
        LocalDate billingDate,

        @Schema(description = "구독의 현재 상태(표시용). 스냅샷이 아니라 지금 값이다", example = "ACTIVE",
                nullable = true)
        SubscriptionStatus status,

        @Schema(description = "삭제 여부(soft delete)", example = "false")
        boolean deleted
) {
}
