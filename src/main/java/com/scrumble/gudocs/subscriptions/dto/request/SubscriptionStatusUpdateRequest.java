package com.scrumble.gudocs.subscriptions.dto.request;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SubscriptionStatusUpdateRequest(
        @Schema(description = "변경할 구독 상태", example = "PAUSED")
        @NotNull(message = "구독 상태는 필수입니다.")
        SubscriptionStatus status
) {
}
