package com.scrumble.gudocs.subscriptions.dto.response;

import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.util.PriceChangeReviewCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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

        @Schema(description = "현재 정지 상태가 시작된 시각. ACTIVE면 null. "
                + "'7월 3일부터 일시정지 중' 같은 표시에 쓴다. 현재 상태에 대한 사실이며, "
                + "과거에 정지했다 재개한 이력은 여기 남지 않는다(지출 상세의 statusInMonth 참고).",
                example = "2026-07-03T14:20:00", nullable = true)
        LocalDateTime pausedAt,

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

        @Schema(description = "이 구독이 쓰는 요금제의 공식 가격 변경 예고. 예고가 없거나 "
                + "현재 금액이 카탈로그 요금제와 다르면(프로모션가 등) null. "
                + "서버가 구독 금액을 자동으로 바꾸지는 않으며, 반영 여부는 사용자가 고른다.")
        PriceChangeResponse priceChange,

        @Schema(description = "공식 가격 적용일 이후 첫 예정 결제일이 지나, 서비스 재접속 시 "
                + "결제 금액 확인 배너를 보여줄지 여부. 실제 카드 결제를 확인한 값은 아니며 "
                + "이 값으로 푸시를 발송하거나 구독 금액을 자동 수정하지 않는다.", example = "false")
        boolean priceReviewRequired,

        @Schema(description = "생성 일시", example = "2026-07-01T12:00:00")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-07-10T12:00:00")
        LocalDateTime updatedAt
) {
    public static SubscriptionResponse from(Subscription subscription, LocalDate nextBillingDate, LocalDate today) {
        Optional<ServiceCatalog.PriceChange> priceChange = ServiceCatalog.priceChangeOf(
                subscription.getServiceCode(), subscription.getPrice(), subscription.getBillingCycle());
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getServiceName(),
                subscription.getServiceCode(),
                subscription.getCategory(),
                subscription.getPrice(),
                subscription.getBillingCycle(),
                subscription.getFirstBillingDate(),
                subscription.getStatus(),
                subscription.getPausedAt(),
                nextBillingDate,
                // 해지 링크는 저장하지 않고 code 로 카탈로그에서 그때그때 찾는다 —
                // 링크가 바뀌면 카탈로그만 고치면 되고, 이미 저장된 구독도 함께 최신 링크를 받는다.
                ServiceCatalog.cancelUrlOf(subscription.getServiceCode()),
                subscription.isSavingsSelected(),
                // 해지 링크와 같은 이유로 저장하지 않고 매번 카탈로그에서 찾는다 —
                // 예고를 고치거나 유지 기간이 끝나 지우면 이미 등록된 구독도 즉시 따라간다.
                priceChange.map(PriceChangeResponse::from).orElse(null),
                priceChange.filter(change -> PriceChangeReviewCalculator.isRequired(subscription, change, today))
                        .isPresent(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
