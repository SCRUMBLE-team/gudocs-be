package com.scrumble.gudocs.subscriptions.dto.response;

import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        @Schema(description = "구독 ID", example = "1")
        Long id,

        @Schema(description = "서비스명(표시용)", example = "넷플릭스")
        String serviceName,

        @Schema(description = "카탈로그 서비스 코드. 프론트는 이 값으로 로고를 찾는다. "
                + "직접 입력한 서비스면 null(로고 없음).", example = "NETFLIX")
        String serviceCode,

        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "결제 금액(원)", example = "17000")
        Long price,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "최초 결제일(다음 결제일 계산의 기준 앵커)", example = "2026-07-15")
        LocalDate firstBillingDate,

        @Schema(description = "구독 상태", example = "ACTIVE")
        SubscriptionStatus status,

        @Schema(description = "다음 결제일", example = "2026-07-31")
        LocalDate nextBillingDate,

        @Schema(description = "해지 페이지 URL. 상세 화면의 '해지하러 가기' 링크로 쓴다. "
                + "직접 입력한 서비스이거나 해지할 결제가 없는 서비스면 null(링크 숨김). "
                + "웹 결제 기준이라 App Store·Google Play 인앱결제로 가입했다면 각 스토어에서 해지해야 한다.",
                example = "https://www.netflix.com/cancelplan")
        String cancelUrl,

        @Schema(description = "절약하기 화면에서 해지 후보로 체크한 구독인지. "
                + "화면 재진입·다른 기기에서도 체크 상태가 유지된다.", example = "false")
        boolean savingsSelected,

        @Schema(description = "생성 일시", example = "2026-07-01T12:00:00")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-07-10T12:00:00")
        LocalDateTime updatedAt
) {
    public static SubscriptionResponse from(Subscription subscription, LocalDate nextBillingDate) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getServiceName(),
                subscription.getServiceCode(),
                subscription.getCategory(),
                subscription.getPrice(),
                subscription.getBillingCycle(),
                subscription.getFirstBillingDate(),
                subscription.getStatus(),
                nextBillingDate,
                // 해지 링크는 저장하지 않고 code 로 카탈로그에서 그때그때 찾는다 —
                // 링크가 바뀌면 카탈로그만 고치면 되고, 이미 저장된 구독도 함께 최신 링크를 받는다.
                ServiceCatalog.cancelUrlOf(subscription.getServiceCode()),
                subscription.isSavingsSelected(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
