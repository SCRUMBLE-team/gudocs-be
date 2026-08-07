package com.scrumble.gudocs.subscriptions.catalog;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCatalogTest {

    @Test
    void canonical_name으로_매칭된다() {
        Optional<ServiceCatalog.CatalogService> result =
                ServiceCatalog.match("[결제 알림] 넷플릭스 17,000원 결제되었습니다.");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("넷플릭스");
        assertThat(result.get().category()).isEqualTo(SubscriptionCategory.OTT);
    }

    @Test
    void 영문_alias로도_매칭되고_canonical_name을_반환한다() {
        Optional<ServiceCatalog.CatalogService> result = ServiceCatalog.match("Netflix Payment Receipt");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("넷플릭스");
    }

    @Test
    void 공백_대소문자를_무시하고_매칭한다() {
        Optional<ServiceCatalog.CatalogService> result = ServiceCatalog.match("CHATGPT PLUS 결제 안내");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("ChatGPT");
        assertThat(result.get().category()).isEqualTo(SubscriptionCategory.AI);
    }

    @Test
    void 괄호가_섞인_표기도_alias와_매칭된다() {
        Optional<ServiceCatalog.CatalogService> result = ServiceCatalog.match("쿠팡(와우 멤버십) 7,890원");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("쿠팡 와우");
    }

    @Test
    void 신규_추가된_국내_서비스도_매칭된다() {
        Optional<ServiceCatalog.CatalogService> baemin =
                ServiceCatalog.match("배민클럽_우아한형제들 1,990원 결제");
        assertThat(baemin).isPresent();
        assertThat(baemin.get().canonicalName()).isEqualTo("배민클럽");
        assertThat(baemin.get().category()).isEqualTo(SubscriptionCategory.SHOPPING);

        Optional<ServiceCatalog.CatalogService> taling = ServiceCatalog.match("탈잉 클래스 결제 안내");
        assertThat(taling).isPresent();
        assertThat(taling.get().canonicalName()).isEqualTo("탈잉");
        assertThat(taling.get().category()).isEqualTo(SubscriptionCategory.EDUCATION);
    }

    @Test
    void 매칭되는_서비스가_없으면_빈값을_반환한다() {
        assertThat(ServiceCatalog.match("알 수 없는 서비스 결제 안내")).isEmpty();
    }

    @Test
    void 쿠팡이츠도_매칭된다() {
        Optional<ServiceCatalog.CatalogService> result = ServiceCatalog.match("쿠팡이츠 결제 안내");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("쿠팡이츠");
    }

    @Test
    void 쿠팡_계열은_최장_매칭으로_서로_섞이지_않는다() {
        assertThat(ServiceCatalog.match("쿠팡이츠 무료배달").get().canonicalName()).isEqualTo("쿠팡이츠");
        assertThat(ServiceCatalog.match("쿠팡플레이 시청").get().canonicalName()).isEqualTo("쿠팡플레이");
        assertThat(ServiceCatalog.match("쿠팡 와우 멤버십").get().canonicalName()).isEqualTo("쿠팡 와우");
    }

    @Test
    void 짧은_영문_alias는_무관한_단어_속에서_오매칭되지_않는다() {
        // "flow"/"anytime" 안의 flo·ea 처럼 단어 일부에 우연히 겹치는 경우
        assertThat(ServiceCatalog.match("workflow 자동화 결제")).isEmpty();
        assertThat(ServiceCatalog.match("anytime fitness 이용료")).isEmpty();
    }

    @Test
    void 짧은_영문_alias도_독립된_단어면_매칭된다() {
        Optional<ServiceCatalog.CatalogService> result = ServiceCatalog.match("FLO 이용권 7,900원");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("FLO");
    }

    @Test
    void 금액으로_요금제를_역추적한다() {
        ServiceCatalog.CatalogService netflix = ServiceCatalog.match("넷플릭스").orElseThrow();

        assertThat(netflix.planByPrice(17000L)).map(ServiceCatalog.Plan::name).contains("프리미엄");
        assertThat(netflix.planByPrice(13500L)).map(ServiceCatalog.Plan::name).contains("스탠다드");
        assertThat(netflix.planByPrice(9999L)).isEmpty();
        assertThat(netflix.planByPrice(null)).isEmpty();
    }

    @Test
    void code는_UPPER_SNAKE_형식이고_중복되지_않는다() {
        // code 는 프론트 로고 파일명과 맞물리는 불변 키다. 형식이 흔들리면 매칭이 깨진다.
        assertThat(ServiceCatalog.services())
                .extracting(ServiceCatalog.CatalogService::code)
                .doesNotHaveDuplicates()
                .allSatisfy(code -> assertThat(code).matches("[A-Z][A-Z0-9_]*"));
    }

    @Test
    void 신규_등록_불가_서비스는_요금제_없이_OCR_매칭_대상으로만_남는다() {
        // 종료된 서비스
        ServiceCatalog.CatalogService clovaX = ServiceCatalog.match("클로바X 결제").orElseThrow();
        assertThat(clovaX.selectable()).isFalse();
        assertThat(clovaX.plans()).isEmpty();

        ServiceCatalog.CatalogService ssg = ServiceCatalog.match("SSG 유니버스클럽 연회비").orElseThrow();
        assertThat(ssg.selectable()).isFalse();

        // 살아 있지만 독립 구독 상품이 아닌 서비스
        ServiceCatalog.CatalogService eats = ServiceCatalog.match("쿠팡이츠 결제 안내").orElseThrow();
        assertThat(eats.selectable()).isFalse();
        assertThat(eats.plans()).isEmpty();
    }

    @Test
    void 스포티파이_요금제가_실제_가격과_일치한다() {
        ServiceCatalog.CatalogService spotify = ServiceCatalog.match("스포티파이").orElseThrow();

        assertThat(spotify.planByPrice(8690L)).map(ServiceCatalog.Plan::name).contains("베이직");
        assertThat(spotify.planByPrice(11990L)).map(ServiceCatalog.Plan::name).contains("개인");
        assertThat(spotify.planByPrice(17985L)).map(ServiceCatalog.Plan::name).contains("듀오");
        assertThat(spotify.planByPrice(6600L)).map(ServiceCatalog.Plan::name).contains("학생");
        // 잘못 적혀 있던 개인 10,900원은 제거됐다.
        assertThat(spotify.planByPrice(10900L)).isEmpty();
    }

    @Test
    void 쿠팡_계열은_와우_멤버십_금액을_한_곳에만_둔다() {
        // 와우 멤버십 금액의 주인은 COUPANG_WOW 하나다. 플레이·이츠에도 같은 금액을 두면
        // 사용자가 둘 다 등록해 실제로 한 번 내는 돈이 지출에 두 번 잡힌다.
        ServiceCatalog.CatalogService wow = ServiceCatalog.findByCode("COUPANG_WOW").orElseThrow();
        assertThat(wow.planByPrice(7890L)).map(ServiceCatalog.Plan::name).contains("와우 멤버십");

        ServiceCatalog.CatalogService play = ServiceCatalog.findByCode("COUPANG_PLAY").orElseThrow();
        assertThat(play.planByPrice(7890L)).isEmpty();
        assertThat(play.planByPrice(12400L)).map(ServiceCatalog.Plan::name)
                .contains("스포츠 패스 (와우회원)");
        assertThat(play.planByPrice(19300L)).map(ServiceCatalog.Plan::name)
                .contains("스포츠 패스 (일반회원)");

        ServiceCatalog.CatalogService eats = ServiceCatalog.findByCode("COUPANG_EATS").orElseThrow();
        assertThat(eats.plans()).isEmpty();
    }

    @Test
    void 신규_등록_불가_서비스는_요금제를_갖지_않는다() {
        assertThat(ServiceCatalog.services())
                .filteredOn(service -> !service.selectable())
                .isNotEmpty()
                .allSatisfy(service -> assertThat(service.plans()).isEmpty());
    }

    @Test
    void 카탈로그_데이터가_정합적이다() {
        assertThat(ServiceCatalog.services()).isNotEmpty();

        assertThat(ServiceCatalog.services())
                .extracting(ServiceCatalog.CatalogService::canonicalName)
                .doesNotHaveDuplicates();

        assertThat(ServiceCatalog.services()).allSatisfy(service -> {
            assertThat(service.canonicalName()).isNotBlank();
            assertThat(service.category()).isNotNull();
            assertThat(service.plans()).allSatisfy(plan -> {
                assertThat(plan.name()).isNotBlank();
                assertThat(plan.price()).isPositive();
                assertThat(plan.billingCycle()).isNotNull();
            });
            assertThat(service.plans()).extracting(ServiceCatalog.Plan::name).doesNotHaveDuplicates();
        });
    }

    @Test
    void 등록된_모든_서비스는_자기_이름으로_매칭된다() {
        assertThat(ServiceCatalog.services()).allSatisfy(service ->
                assertThat(ServiceCatalog.match(service.canonicalName()))
                        .as("서비스 %s", service.canonicalName())
                        .isPresent());
    }
}
