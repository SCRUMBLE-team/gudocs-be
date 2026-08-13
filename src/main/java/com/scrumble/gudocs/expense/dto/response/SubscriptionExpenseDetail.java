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

        @Schema(description = "그 달에 청구 예정일이 도래한 금액(원). 청구가 없었으면 0 — "
                + "연간 구독의 커버 중인 달(청구는 작년), 그 달 정지, 청구일이 아직 안 온 경우가 그렇다. "
                + "프론트가 결제주기·앵커로 '이 달 청구인지'를 되계산하지 않아도 되게 서버가 내려준다.",
                example = "17000")
        long billedAmount,

        @Schema(description = "최초 결제일(다음 결제일 계산의 기준 앵커). 구독의 현재 값이며, "
                + "그 달의 청구일은 billingDate", example = "2026-01-15")
        LocalDate firstBillingDate,

        @Schema(description = "그 달에 청구 예정일이 도래한 날짜. 연간 구독처럼 그 달에 청구가 없고 "
                + "이전 청구가 커버 중이면 그 청구일(과거 날짜)이 들어간다. 그 달에 청구 기록이 "
                + "하나도 없으면(정지 등) null", example = "2026-07-15", nullable = true)
        LocalDate billingDate,

        @Schema(description = "구독의 현재 상태(표시용). 스냅샷이 아니라 지금 값이다", example = "ACTIVE",
                nullable = true)
        SubscriptionStatus status,

        @Schema(description = "그 달 기준 상태(표시용). 정지 구간 이력으로 판정하며 그 달 말(진행 중인 "
                + "달이면 오늘) 시점을 본다. 현재 상태(status)와 달리 과거 달에도 맞는 값이다. "
                + "이력이 남기 전(2026-08 이전)의 정지·재개는 복원할 수 없어 ACTIVE로 답한다. "
                + "표시용 라벨일 뿐이며 이 값으로 금액을 계산하지 않는다", example = "PAUSED",
                nullable = true)
        SubscriptionStatus statusInMonth,

        @Schema(description = "삭제 여부(soft delete)", example = "false")
        boolean deleted
) {
}
