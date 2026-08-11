package com.scrumble.gudocs.subscriptions.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가격 변경 예고가 응답으로 나가는 경로를 <b>선언 여부와 무관하게</b> 검증한다.
 * <p>
 * 카탈로그의 실제 선언은 인상 발표가 있을 때만 들어 있어서, 실제 데이터에 기대면 평상시에는
 * "예고 없음" 경로만 검증된다 — 변환이나 직렬화가 깨져도 테스트가 통과한다.
 * 그래서 예고가 붙은 서비스를 여기서 직접 만들어 넣는다.
 */
// @JsonTest 슬라이스는 JPA 엔티티를 제외해 @EnableJpaAuditing 이 뜨지 못한다.
// 다른 테스트가 이미 띄우는 컨텍스트를 재사용하므로 전체 컨텍스트라도 비용은 거의 없다.
@SpringBootTest
class PriceChangeResponseTest {

    private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 9, 1);
    private static final LocalDate ANNOUNCED_ON = LocalDate.of(2026, 8, 5);
    private static final String SOURCE_URL = "https://help.netflix.com/ko/node/example";

    /** 애플리케이션이 실제로 응답에 쓰는 매퍼. 직접 만든 매퍼로 검증하면 내 설정을 검증하는 셈이 된다. */
    @Autowired
    private ObjectMapper objectMapper;

    private ServiceCatalog.CatalogService serviceWithDeclaredChange() {
        ServiceCatalog.Plan premium =
                new ServiceCatalog.Plan("프리미엄", 17000L, BillingCycle.MONTHLY, false, null)
                        .changingTo(19000L, EFFECTIVE_ON, ANNOUNCED_ON, SOURCE_URL);
        ServiceCatalog.Plan standard =
                new ServiceCatalog.Plan("스탠다드", 13500L, BillingCycle.MONTHLY, false, null);
        return new ServiceCatalog.CatalogService("NETFLIX", "넷플릭스", SubscriptionCategory.OTT,
                true, "https://www.netflix.com/cancelplan", List.of(), List.of(premium, standard));
    }

    @Test
    void 예고가_붙은_요금제만_카탈로그_응답에_변경_정보를_싣는다() {
        CatalogResponse response = CatalogResponse.from(List.of(serviceWithDeclaredChange()));

        List<CatalogResponse.CatalogPlanResponse> plans = response.services().get(0).plans();
        CatalogResponse.CatalogPlanResponse premium = plans.get(0);
        assertThat(premium.priceChange()).isNotNull();
        assertThat(premium.priceChange().newPrice()).isEqualTo(19000L);
        assertThat(premium.priceChange().effectiveOn()).isEqualTo(EFFECTIVE_ON);
        assertThat(premium.priceChange().announcedOn()).isEqualTo(ANNOUNCED_ON);
        assertThat(premium.priceChange().sourceUrl()).isEqualTo(SOURCE_URL);
        // 변경 전 금액은 따로 싣지 않는다 — 그 요금제의 price 가 곧 구가격이다.
        assertThat(premium.price()).isEqualTo(17000L);

        assertThat(plans.get(1).priceChange()).isNull();
    }

    @Test
    void 프론트가_받는_필드명과_날짜_표기를_고정한다() throws Exception {
        String json = objectMapper.writeValueAsString(
                PriceChangeResponse.from(new ServiceCatalog.PriceChange(
                        19000L, EFFECTIVE_ON, ANNOUNCED_ON, SOURCE_URL)));

        assertThat(json).contains("\"newPrice\":19000")
                .contains("\"effectiveOn\":\"2026-09-01\"")
                .contains("\"announcedOn\":\"2026-08-05\"")
                .contains("\"sourceUrl\":\"" + SOURCE_URL + "\"");
    }
}
