package com.scrumble.gudocs.subscriptions.dto.response;

import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 공식 발표된 가격 변경 예고. 구독 상세와 등록 화면 요금제에 함께 실린다.
 *
 * <p>변경 전 금액은 싣지 않는다 — 구독 응답에서는 그 구독의 {@code price}가, 카탈로그 응답에서는
 * 그 요금제의 {@code price}가 곧 변경 전 금액이다.
 *
 * <p><b>서버는 사용자의 결제 금액을 자동으로 바꾸지 않는다.</b> 기존가 유지·프로모션·제휴결합·인앱결제로
 * 사람마다 실제 청구액이 달라서다. 프론트는 이 정보를 안내로 띄우고, 사용자가 "내 구독료에 반영"을
 * 누르면 기존 구독 수정 API로 금액을 바꾼다.
 */
@Schema(description = "공식 가격 변경 예고")
public record PriceChangeResponse(
        @Schema(description = "변경 후 금액(원)", example = "19000")
        Long newPrice,

        @Schema(description = "실제 적용 예정일", example = "2026-09-01")
        LocalDate effectiveOn,

        @Schema(description = "공식 발표일", example = "2026-08-05")
        LocalDate announcedOn,

        @Schema(description = "공식 출처 URL. 사용자가 원문을 확인할 수 있게 화면에 링크로 노출한다.",
                example = "https://help.netflix.com/ko/node/24926")
        String sourceUrl
) {
    public static PriceChangeResponse from(ServiceCatalog.PriceChange change) {
        return new PriceChangeResponse(
                change.newPrice(), change.effectiveOn(), change.announcedOn(), change.sourceUrl());
    }
}
