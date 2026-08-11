package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 결제 알림의 "대상 선별 + 결제일 묶음 + draft 구성" 책임만 검증한다.
 * 실제 발송/중복방지/재시도는 {@link NotificationSender}(별도 테스트)에 위임한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);
    private static final Long USER_ID = 1L;

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private NotificationDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispatchService, "frontendBaseUrl", "https://gudocs-fe-v2.vercel.app");
    }

    private Subscription sub(Long id, String name, long price, LocalDate firstBillingDate) {
        User user = User.builder().id(USER_ID).name("테스터").email("t@e.com").build();
        return Subscription.builder()
                .id(id).user(user).serviceName(name)
                .category(SubscriptionCategory.OTT).price(price)
                .billingCycle(BillingCycle.MONTHLY).firstBillingDate(firstBillingDate)
                .build();
    }

    @Test
    void 당일_결제_단건_draft_구성() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(sub(100L, "Netflix", 17000L, TODAY)));

        dispatchService.dispatchDueReminders(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender).send(eq(USER_ID), captor.capture());
        NotificationDraft draft = captor.getValue();
        assertThat(draft.type()).isEqualTo(NotificationType.BILLING_REMINDER);
        assertThat(draft.targetDate()).isEqualTo(TODAY);
        assertThat(draft.remindOffset()).isZero();          // 당일 = D-0
        assertThat(draft.title()).isEqualTo("Netflix 결제 예정");
        assertThat(draft.body()).contains("오늘").contains("17,000원");
    }

    @Test
    void 같은_결제일_여러_구독은_한_draft로_묶임() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(
                        sub(100L, "Netflix", 17000L, TODAY),
                        sub(101L, "Spotify", 10900L, TODAY)));

        dispatchService.dispatchDueReminders(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender, times(1)).send(eq(USER_ID), captor.capture());
        NotificationDraft draft = captor.getValue();
        assertThat(draft.title()).contains("외 1건");
        assertThat(draft.body()).contains("2건").contains("27,900원"); // 합산 금액
    }

    @Test
    void D3와_당일은_결제일이_다르므로_별도_draft() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(
                        sub(100L, "Netflix", 17000L, TODAY),               // D-0
                        sub(101L, "Spotify", 10900L, TODAY.plusDays(3))));  // D-3

        dispatchService.dispatchDueReminders(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender, times(2)).send(eq(USER_ID), captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationDraft::remindOffset)
                .containsExactlyInAnyOrder(0, 3);
    }

    /** 절약 후보로 체크해 둔 구독. */
    private Subscription savingsSelected(Long id, String name, long price, LocalDate firstBillingDate) {
        Subscription subscription = sub(id, name, price, firstBillingDate);
        subscription.updateSavingsSelection(true);
        return subscription;
    }

    @Test
    void 절약_후보의_D3는_해지_알림으로_대체된다() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(savingsSelected(100L, "Netflix", 17000L, TODAY.plusDays(3))));

        dispatchService.dispatchDueReminders(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender, times(1)).send(eq(USER_ID), captor.capture());
        NotificationDraft draft = captor.getValue();
        assertThat(draft.type()).isEqualTo(NotificationType.CANCEL_REMINDER);
        assertThat(draft.remindOffset()).isEqualTo(3);
        assertThat(draft.title()).isEqualTo("Netflix 해지하실 건가요?");
        assertThat(draft.body()).contains("3일 후").contains("17,000원");
        // 클릭하면 그 구독 상세로 랜딩해야 하므로 구독 id 를 싣는다.
        assertThat(draft.subscriptionId()).isEqualTo(100L);
        assertThat(draft.pushData()).containsEntry("subscriptionId", "100")
                .containsEntry("link", "https://gudocs-fe-v2.vercel.app/subscriptions/100");
    }

    @Test
    void 절약_후보는_결제_예정_묶음에서_빠진다() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(
                        savingsSelected(100L, "Netflix", 17000L, TODAY.plusDays(3)),
                        sub(101L, "Spotify", 10900L, TODAY.plusDays(3))));

        dispatchService.dispatchDueReminders(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender, times(2)).send(eq(USER_ID), captor.capture());

        NotificationDraft cancel = captor.getAllValues().stream()
                .filter(d -> d.type() == NotificationType.CANCEL_REMINDER).findFirst().orElseThrow();
        NotificationDraft billing = captor.getAllValues().stream()
                .filter(d -> d.type() == NotificationType.BILLING_REMINDER).findFirst().orElseThrow();

        assertThat(cancel.title()).contains("Netflix");
        // 결제 예정 알림은 Spotify 만 남는다 — 묶음에 남으면 같은 구독을 두 번 알리는 셈이 된다.
        assertThat(billing.title()).isEqualTo("Spotify 결제 예정");
        assertThat(billing.body()).contains("10,900원").doesNotContain("27,900원");
        assertThat(billing.subscriptionId()).isEqualTo(0L);
    }

    @Test
    void 절약_후보라도_당일_결제는_기존_결제_알림으로_나간다() {
        // 당일은 이미 빠져나가는 돈이라 해지 권유보다 결제 사실 통지가 맞다.
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(savingsSelected(100L, "Netflix", 17000L, TODAY)));

        dispatchService.dispatchDueReminders(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender).send(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.BILLING_REMINDER);
        assertThat(captor.getValue().remindOffset()).isZero();
    }

    @Test
    void 절약_후보_여러_건이_같은_날이면_구독별로_발송된다() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(
                        savingsSelected(100L, "Netflix", 17000L, TODAY.plusDays(3)),
                        savingsSelected(101L, "Spotify", 10900L, TODAY.plusDays(3))));

        dispatchService.dispatchDueReminders(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender, times(2)).send(eq(USER_ID), captor.capture());
        // dedup 키에 구독 id 가 들어가므로 같은 날 같은 단계라도 서로 다른 알림으로 취급된다.
        assertThat(captor.getAllValues()).extracting(NotificationDraft::subscriptionId)
                .containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    void D3_당일이_아닌_시점은_발송하지_않음() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(
                        sub(100L, "A", 1000L, TODAY.plusDays(1)),   // D-1
                        sub(101L, "B", 1000L, TODAY.plusDays(7))));  // D-7

        dispatchService.dispatchDueReminders(TODAY);

        verify(notificationSender, never()).send(eq(USER_ID), org.mockito.ArgumentMatchers.any());
    }
}
