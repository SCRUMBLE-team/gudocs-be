package com.scrumble.gudocs.subscriptions.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "절약하기 화면에서 해지 후보로 체크한 구독 목록")
public record SavingsSelectionRequest(

        @Schema(description = "체크한 구독 ID 목록. 현재 선택을 이 목록으로 통째로 대체한다. "
                + "빈 배열이면 전체 선택 해제. 모두 로그인한 사용자의 구독이어야 한다.",
                example = "[1, 2, 5]")
        @NotNull(message = "subscriptionIds는 필수입니다.")
        List<Long> subscriptionIds
) {
}
