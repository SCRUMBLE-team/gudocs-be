package com.scrumble.gudocs.subscriptions.dto.response;

import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 공식 발표된 가격 변경 예고. 구독 상세와 등록 화면 요금제에 함께 실린다.
 *
 * <p>{@code oldPrice}를 함께 싣는다. 적용일이 지나면 요금제의 {@code price}가 새 금액으로 올라가
 * 구가격을 알 방법이 없어지는데, 배너는 그 뒤에도 "17,000원 → 19,000원"을 보여줘야 하기 때문이다.
 *
 * <p><b>서버는 사용자의 결제 금액을 자동으로 바꾸지 않는다.</b> 기존가 유지·프로모션·제휴결합·인앱결제로
 * 사람마다 실제 청구액이 달라서다. 프론트는 구독 상세 진입 시 이 정보로 "내 구독료에 반영할까요?"를
 * 묻고, 사용자가 반영을 고르면 기존 구독 수정 API로 금액을 바꾼다.
 *
 * <p><b>반영하고 나면 이 필드는 저절로 사라진다</b> — 구독의 금액이 더 이상 변경 대상 요금제의
 * 금액과 일치하지 않기 때문이다. 따로 "확인했음" 상태를 저장하지 않아도 같은 팝업이 다시 뜨지 않는다.
 */
@Schema(description = "공식 가격 변경 예고")
public record PriceChangeResponse(
        @Schema(description = "변경 전 금액(원). 이 금액을 쓰는 구독이 안내 대상이다.", example = "17000")
        Long oldPrice,

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
        return new PriceChangeResponse(change.oldPrice(), change.newPrice(),
                change.effectiveOn(), change.announcedOn(), change.sourceUrl());
    }
}
