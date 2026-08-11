package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog.DeclaredPriceChange;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 가격 변경 알림의 "대상 선별 + draft 구성" 책임만 검증한다.
 * 실제 발송/중복방지는 {@link NotificationSender}(별도 테스트)에 위임한다.
 */
@ExtendWith(MockitoExtension.class)
class PriceChangeDispatchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 11);
    private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 9, 1);
    private static final Long USER_ID = 1L;

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private PriceChangeDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispatchService, "frontendBaseUrl", "https://gudocs-fe-v2.vercel.app");
    }

    /** 넷플릭스 프리미엄 17,000 → 19,000원, 9월 1일 적용. */
    private DeclaredPriceChange netflixPremium(LocalDate effectiveOn) {
        ServiceCatalog.CatalogService netflix = ServiceCatalog.findByCode("NETFLIX").orElseThrow();
        ServiceCatalog.Plan premium =
                new ServiceCatalog.Plan("프리미엄", 17000L, BillingCycle.MONTHLY, false, null)
                        .changingTo(19000L, effectiveOn, LocalDate.of(2026, 8, 5),
                                "https://help.netflix.com/ko/node/example");
        return new DeclaredPriceChange(netflix, premium, premium.change());
    }

    private Subscription sub(Long id, long price) {
        User user = User.builder().id(USER_ID).name("테스터").email("t@e.com").build();
        return Subscription.builder()
                .id(id).user(user).serviceName("넷플릭스").serviceCode("NETFLIX")
                .category(SubscriptionCategory.OTT).price(price)
                .billingCycle(BillingCycle.MONTHLY).firstBillingDate(TODAY)
                .build();
    }

    @Test
    void 변경_대상_요금제_사용자에게_draft를_구성한다() {
        given(subscriptionRepository.findActiveByServiceCodeAndPriceAndBillingCycle(
                "NETFLIX", 17000L, BillingCycle.MONTHLY))
                .willReturn(List.of(sub(100L, 17000L)));

        dispatchService.dispatch(List.of(netflixPremium(EFFECTIVE_ON)), TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender).send(eq(USER_ID), captor.capture());
        NotificationDraft draft = captor.getValue();
        assertThat(draft.type()).isEqualTo(NotificationType.PRICE_CHANGE);
        // dedup 키의 targetDate 가 적용 예정일 = "이 인상 건"의 식별자 역할을 겸한다.
        assertThat(draft.targetDate()).isEqualTo(EFFECTIVE_ON);
        assertThat(draft.remindOffset()).isZero();
        assertThat(draft.title()).isEqualTo("넷플릭스 프리미엄 가격이 바뀌어요");
        assertThat(draft.body())
                .isEqualTo("17,000원 → 19,000원, 9월 1일부터 적용돼요. 실제 결제 금액은 다를 수 있어요.");
        // 클릭하면 그 구독 상세로 랜딩해 "내 구독료에 반영"까지 이어져야 한다.
        assertThat(draft.subscriptionId()).isEqualTo(100L);
        assertThat(draft.pushData()).containsEntry("subscriptionId", "100")
                .containsEntry("link", "https://gudocs-fe-v2.vercel.app/subscriptions/100");
    }

    @Test
    void 다른_금액을_쓰는_사용자는_조회_대상에서_빠진다() {
        // 프로모션가·구요금제·다른 요금제 사용자는 애초에 이번 변경 대상이 아니다.
        // 구독에 요금제명이 없으므로 "금액+주기 일치"가 곧 요금제 판정이다.
        given(subscriptionRepository.findActiveByServiceCodeAndPriceAndBillingCycle(
                "NETFLIX", 17000L, BillingCycle.MONTHLY))
                .willReturn(List.of());

        dispatchService.dispatch(List.of(netflixPremium(EFFECTIVE_ON)), TODAY);

        verify(notificationSender, never()).send(any(), any());
    }

    @Test
    void 적용일이_지난_예고는_조회조차_하지_않는다() {
        // 카탈로그에서 지우는 것을 잊어도 뒤늦게 "곧 바뀌어요" 알림이 나가지 않아야 한다.
        dispatchService.dispatch(List.of(netflixPremium(TODAY.minusDays(1))), TODAY);

        verifyNoInteractions(subscriptionRepository, notificationSender);
    }

    @Test
    void 적용_당일에는_아직_발송한다() {
        // 그날부터 적용이라 마지막으로 알릴 가치가 있다(이미 지난 날짜만 건너뛴다).
        given(subscriptionRepository.findActiveByServiceCodeAndPriceAndBillingCycle(
                "NETFLIX", 17000L, BillingCycle.MONTHLY))
                .willReturn(List.of(sub(100L, 17000L)));

        dispatchService.dispatch(List.of(netflixPremium(TODAY)), TODAY);

        verify(notificationSender).send(eq(USER_ID), any());
    }

    @Test
    void 같은_요금제_구독이_여럿이면_구독별로_발송된다() {
        given(subscriptionRepository.findActiveByServiceCodeAndPriceAndBillingCycle(
                "NETFLIX", 17000L, BillingCycle.MONTHLY))
                .willReturn(List.of(sub(100L, 17000L), sub(101L, 17000L)));

        dispatchService.dispatch(List.of(netflixPremium(EFFECTIVE_ON)), TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender, times(2)).send(eq(USER_ID), captor.capture());
        // dedup 키에 구독 id 가 들어가므로 같은 인상 건이라도 서로 다른 알림으로 취급된다.
        assertThat(captor.getAllValues()).extracting(NotificationDraft::subscriptionId)
                .containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    void 선언된_예고가_없으면_아무것도_하지_않는다() {
        // 평상시(인상 발표가 없는 날)의 동작 — 대부분의 날이 여기에 해당한다.
        dispatchService.dispatch(List.of(), TODAY);

        verifyNoInteractions(subscriptionRepository, notificationSender);
    }
}
