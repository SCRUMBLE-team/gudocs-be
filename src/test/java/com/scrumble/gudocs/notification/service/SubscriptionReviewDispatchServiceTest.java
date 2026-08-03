package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionReviewDispatchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);
    private static final Long USER_ID = 1L;

    @Mock
    private PushRegistrationRepository pushRegistrationRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private SubscriptionReviewDispatchService reviewDispatchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewDispatchService, "frontendBaseUrl", "https://gudocs-fe-v2.vercel.app");
    }

    /** 가입 후 daysAgo일 경과한 유저 (createdAt = TODAY - daysAgo). */
    private User userSignedUpDaysAgo(long daysAgo) {
        User user = User.builder().id(USER_ID).name("테스터").email("t@e.com").build();
        ReflectionTestUtils.setField(user, "createdAt", TODAY.minusDays(daysAgo).atStartOfDay());
        return user;
    }

    private Subscription sub(SubscriptionCategory category) {
        return Subscription.builder()
                .user(User.builder().id(USER_ID).build())
                .serviceName("svc").category(category).price(1000L)
                .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2026, 1, 1))
                .build();
    }

    private void givenReachableUser(User user, List<Subscription> subscriptions) {
        given(pushRegistrationRepository.findDistinctUserIdsWithEnabledRegistration())
                .willReturn(List.of(USER_ID));
        given(subscriptionRepository.findActiveForBillingReminder()).willReturn(subscriptions);
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user));
    }

    @Test
    void 중복있음_가입14일_발송() {
        givenReachableUser(userSignedUpDaysAgo(14),
                List.of(sub(SubscriptionCategory.OTT), sub(SubscriptionCategory.OTT)));

        reviewDispatchService.dispatchDueReviews(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender).send(eq(USER_ID), captor.capture());
        NotificationDraft draft = captor.getValue();
        assertThat(draft.type()).isEqualTo(NotificationType.SUBSCRIPTION_REVIEW);
        assertThat(draft.targetDate()).isEqualTo(TODAY);
        assertThat(draft.title()).contains("중복");
    }

    @Test
    void 중복없음_가입28일_발송() {
        givenReachableUser(userSignedUpDaysAgo(28),
                List.of(sub(SubscriptionCategory.OTT), sub(SubscriptionCategory.MUSIC)));

        reviewDispatchService.dispatchDueReviews(TODAY);

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notificationSender).send(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("구독 점검 알림");
    }

    @Test
    void 중복없음_가입14일은_미발송() {
        // 중복 없으면 4주(28일) 주기 → 14일은 발송일 아님
        givenReachableUser(userSignedUpDaysAgo(14),
                List.of(sub(SubscriptionCategory.OTT), sub(SubscriptionCategory.MUSIC)));

        reviewDispatchService.dispatchDueReviews(TODAY);

        verify(notificationSender, never()).send(anyLong(), any());
    }

    @Test
    void 중복있음_가입28일도_발송() {
        // 14의 배수(28)이므로 중복 있으면 발송
        givenReachableUser(userSignedUpDaysAgo(28),
                List.of(sub(SubscriptionCategory.OTT), sub(SubscriptionCategory.OTT)));

        reviewDispatchService.dispatchDueReviews(TODAY);

        verify(notificationSender, times(1)).send(eq(USER_ID), any());
    }

    @Test
    void 가입당일은_미발송() {
        givenReachableUser(userSignedUpDaysAgo(0), List.of());

        reviewDispatchService.dispatchDueReviews(TODAY);

        verify(notificationSender, never()).send(anyLong(), any());
    }

    @Test
    void 구독_없어도_가입28일이면_발송() {
        // 구독 0개 → 중복 없음 → 4주 주기
        givenReachableUser(userSignedUpDaysAgo(28), List.of());

        reviewDispatchService.dispatchDueReviews(TODAY);

        verify(notificationSender, times(1)).send(eq(USER_ID), any());
    }

    @Test
    void 활성_기기가_없으면_아무것도_안함() {
        given(pushRegistrationRepository.findDistinctUserIdsWithEnabledRegistration())
                .willReturn(List.of());

        reviewDispatchService.dispatchDueReviews(TODAY);

        verify(subscriptionRepository, never()).findActiveForBillingReminder();
        verify(notificationSender, never()).send(anyLong(), any());
    }
}
