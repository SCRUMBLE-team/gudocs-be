package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KnownServiceRegistryTest {

    @Test
    void canonical_name으로_매칭된다() {
        Optional<KnownServiceRegistry.KnownService> result =
                KnownServiceRegistry.match("[결제 알림] 넷플릭스 17,000원 결제되었습니다.");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("넷플릭스");
        assertThat(result.get().category()).isEqualTo(SubscriptionCategory.OTT);
    }

    @Test
    void 영문_alias로도_매칭되고_canonical_name을_반환한다() {
        Optional<KnownServiceRegistry.KnownService> result =
                KnownServiceRegistry.match("Netflix Payment Receipt");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("넷플릭스");
    }

    @Test
    void 공백_대소문자를_무시하고_매칭한다() {
        Optional<KnownServiceRegistry.KnownService> result =
                KnownServiceRegistry.match("CHATGPT PLUS 결제 안내");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("ChatGPT");
        assertThat(result.get().category()).isEqualTo(SubscriptionCategory.AI);
    }

    @Test
    void 괄호가_섞인_표기도_alias와_매칭된다() {
        Optional<KnownServiceRegistry.KnownService> result =
                KnownServiceRegistry.match("쿠팡(와우 멤버십) 7,890원");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("쿠팡 와우");
    }

    @Test
    void 신규_추가된_국내_서비스도_매칭된다() {
        assertThat(KnownServiceRegistry.match("배민클럽_우아한형제들 1,990원 결제").get().canonicalName())
                .isEqualTo("배민클럽");
        assertThat(KnownServiceRegistry.match("탈잉 클래스 결제 안내").get().canonicalName())
                .isEqualTo("탈잉");
    }

    @Test
    void 매칭되는_서비스가_없으면_빈값을_반환한다() {
        assertThat(KnownServiceRegistry.match("알 수 없는 서비스 결제 안내")).isEmpty();
    }
}
