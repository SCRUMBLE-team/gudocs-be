package com.scrumble.gudocs.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record UpcomingNotification(
        @Schema(description = "구독 ID", example = "1")
        Long subscriptionId,

        @Schema(description = "서비스명", example = "Netflix")
        String serviceName,

        @Schema(description = "결제 금액(원)", example = "17000")
        Long price,

        @Schema(description = "다음 결제일", example = "2026-07-31")
        LocalDate nextBillingDate,

        @Schema(description = "결제까지 남은 일수", example = "3")
        int daysUntilBilling
) {
}
