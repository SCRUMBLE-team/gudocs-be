package com.scrumble.gudocs.subscriptions.dto.response;

import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "구독 등록 화면에서 쓰는 서비스·요금제 카탈로그")
public record CatalogResponse(
        @Schema(description = "요금을 공개 자료로 확인한 날짜. 이후 인상분은 반영되지 않았을 수 있다.",
                example = "2026-08-08")
        LocalDate pricesCheckedOn,

        @Schema(description = "서비스 목록")
        List<CatalogServiceResponse> services
) {

    @Schema(description = "카탈로그 서비스 1건")
    public record CatalogServiceResponse(
            @Schema(description = "불변 식별자. 프론트는 이 값으로 로고를 찾는다(파일명 = code). 절대 바뀌지 않는다.",
                    example = "NETFLIX")
            String code,

            @Schema(description = "표시용 서비스명. 오타 수정·브랜드 변경으로 바뀔 수 있으니 키로 쓰지 말 것.",
                    example = "넷플릭스")
            String name,

            @Schema(description = "카테고리", example = "OTT")
            SubscriptionCategory category,

            @Schema(description = "신규 등록 선택 가능 여부. false면 등록 화면 선택지에서 제외한다 "
                    + "(종료됐거나 독립 구독 상품이 아닌 서비스 — 과거 영수증 OCR 인식용으로만 남아 있다).",
                    example = "true")
            boolean selectable,

            @Schema(description = "요금제 목록. 원화 정가를 확인하지 못한 서비스는 빈 배열이며 사용자가 직접 입력한다.")
            List<CatalogPlanResponse> plans
    ) {
    }

    @Schema(description = "요금제 1건")
    public record CatalogPlanResponse(
            @Schema(description = "요금제명", example = "프리미엄")
            String name,

            @Schema(description = "금액(원)", example = "17000")
            Long price,

            @Schema(description = "결제 주기", example = "MONTHLY")
            BillingCycle billingCycle
    ) {
    }

    public static CatalogResponse from(List<ServiceCatalog.CatalogService> services) {
        return new CatalogResponse(
                ServiceCatalog.PRICES_CHECKED_ON,
                services.stream().map(CatalogResponse::toServiceResponse).toList());
    }

    private static CatalogServiceResponse toServiceResponse(ServiceCatalog.CatalogService service) {
        // aliases 는 OCR 매칭 전용이라 응답에 내보내지 않는다.
        return new CatalogServiceResponse(
                service.code(),
                service.canonicalName(),
                service.category(),
                service.selectable(),
                service.plans().stream()
                        .map(plan -> new CatalogPlanResponse(plan.name(), plan.price(), plan.billingCycle()))
                        .toList());
    }
}
