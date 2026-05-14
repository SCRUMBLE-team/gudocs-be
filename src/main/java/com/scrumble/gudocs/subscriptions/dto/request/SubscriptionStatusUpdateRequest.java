package com.scrumble.gudocs.subscriptions.dto.request;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

public record SubscriptionStatusUpdateRequest(
        @NotNull(message = "구독 상태는 필수입니다.")
        SubscriptionStatus status
) {
}
