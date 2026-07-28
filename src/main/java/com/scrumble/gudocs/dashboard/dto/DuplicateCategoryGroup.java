package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DuplicateCategoryGroup(
        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "해당 카테고리의 ACTIVE 구독 목록")
        List<DuplicateSubscriptionItem> subscriptions
) {
}
