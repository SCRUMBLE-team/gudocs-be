package com.scrumble.gudocs.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record InspectionResponse(
        @Schema(description = "미사용 구독 후보 (6개월 이상 정보 미변경 ACTIVE 구독)")
        List<UnusedSubscriptionCandidate> unusedCandidates,

        @Schema(description = "카테고리 중복 구독 후보 (ETC 제외, 같은 카테고리 ACTIVE 2개 이상)")
        List<DuplicateCategoryGroup> duplicateGroups
) {
}
