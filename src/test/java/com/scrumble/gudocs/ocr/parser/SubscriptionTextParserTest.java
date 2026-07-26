package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 25);

    @Test
    void 결제_알림_캡처_텍스트를_파싱한다() {
        String text = "[카드승인]\n홍길동님 카드\n넷플릭스\n17,000원 결제\n2026.07.15";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.serviceName()).isEqualTo("넷플릭스");
        assertThat(result.category()).isEqualTo(SubscriptionCategory.OTT);
        assertThat(result.price()).isEqualTo(17000L);
        assertThat(result.firstBillingDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(result.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }

    @Test
    void 영수증_텍스트에서_연도_없는_날짜는_현재_연도로_채운다() {
        String text = "스포티파이 프리미엄\n결제일: 7월 20일\n금액 11,900원\n간편결제";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.serviceName()).isEqualTo("스포티파이");
        assertThat(result.firstBillingDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.SIMPLE_PAY);
    }

    @Test
    void 연도_포함_날짜와_연_구독_문구가_있으면_YEARLY로_판단한다() {
        String text = "Adobe CC 연간 구독\n2026-03-01 결제\n120,000원 계좌이체";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void 매칭되는_서비스가_없으면_원문_첫줄을_best_effort로_반환하고_카테고리는_null이다() {
        String text = "이상한서비스 정기결제\n5,000원";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.serviceName()).isEqualTo("이상한서비스 정기결제");
        assertThat(result.category()).isNull();
    }

    @Test
    void 일시불_할부는_카드_결제로_인식한다() {
        String text = "7월 15일 수요일\n배민클럽_우아한형제들\n1,990원\n일시불";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    void 아무것도_인식하지_못하면_전_필드가_null이거나_기본값이다() {
        OcrSubscriptionResult result = SubscriptionTextParser.parse("", TODAY);

        assertThat(result.serviceName()).isNull();
        assertThat(result.category()).isNull();
        assertThat(result.price()).isNull();
        assertThat(result.firstBillingDate()).isNull();
        assertThat(result.paymentMethod()).isNull();
        assertThat(result.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }
}
