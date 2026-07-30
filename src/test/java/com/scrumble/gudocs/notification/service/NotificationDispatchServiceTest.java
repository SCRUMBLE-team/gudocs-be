package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushResult;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);
    private static final Long USER_ID = 1L;
    private static final Long SUB_ID = 100L;

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PushRegistrationRepository pushRegistrationRepository;
    @Mock
    private UserNotificationRepository userNotificationRepository;
    @Mock
    private PushSender pushSender;

    @InjectMocks
    private NotificationDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispatchService, "frontendBaseUrl", "https://gudocs-fe-v2.vercel.app");
    }

    private Subscription dueSubscription() {
        User user = User.builder().id(USER_ID).name("테스터").email("t@e.com").build();
        return Subscription.builder()
                .id(SUB_ID)
                .user(user)
                .serviceName("Netflix")
                .category(SubscriptionCategory.OTT)
                .price(17000L)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(TODAY) // 오늘 결제 → 대상
                .build();
    }

    private PushRegistration registration(Long id, String fid) {
        return PushRegistration.builder()
                .id(id).fid(fid).platform(PushPlatform.WEB).enabled(true)
                .build();
    }

    private void givenOneDueSubscriptionNotYetSent() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(dueSubscription()));
        given(userNotificationRepository.existsByUserIdAndSubscriptionIdAndTypeAndTargetDate(
                eq(USER_ID), eq(SUB_ID), eq(NotificationType.BILLING_REMINDER), any()))
                .willReturn(false);
        given(userNotificationRepository.saveAndFlush(any(UserNotification.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 활성_FID_여러개에_각각_발송() {
        givenOneDueSubscriptionNotYetSent();
        given(pushRegistrationRepository.findByUserIdAndEnabledTrue(USER_ID))
                .willReturn(List.of(registration(1L, "fid-A"), registration(2L, "fid-B")));
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.SUCCESS);

        dispatchService.dispatchDueReminders(TODAY);

        verify(pushSender).send(eq("fid-A"), any(PushMessage.class));
        verify(pushSender).send(eq("fid-B"), any(PushMessage.class));
        verify(userNotificationRepository).save(any(UserNotification.class)); // sentAt 기록
    }

    @Test
    void 한_FID_실패해도_다른_FID는_계속_발송() {
        givenOneDueSubscriptionNotYetSent();
        given(pushRegistrationRepository.findByUserIdAndEnabledTrue(USER_ID))
                .willReturn(List.of(registration(1L, "fid-A"), registration(2L, "fid-B")));
        given(pushSender.send(eq("fid-A"), any(PushMessage.class))).willReturn(PushResult.FAILED);
        given(pushSender.send(eq("fid-B"), any(PushMessage.class))).willReturn(PushResult.SUCCESS);

        dispatchService.dispatchDueReminders(TODAY);

        verify(pushSender).send(eq("fid-A"), any(PushMessage.class));
        verify(pushSender).send(eq("fid-B"), any(PushMessage.class));
    }

    @Test
    void 무효_FID는_비활성화() {
        givenOneDueSubscriptionNotYetSent();
        PushRegistration invalid = registration(1L, "fid-A");
        given(pushRegistrationRepository.findByUserIdAndEnabledTrue(USER_ID))
                .willReturn(List.of(invalid));
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.INVALID_TOKEN);

        dispatchService.dispatchDueReminders(TODAY);

        assertThat(invalid.isEnabled()).isFalse();
        verify(pushRegistrationRepository).save(invalid);
        verify(userNotificationRepository, never()).save(any()); // 성공 발송 없음 → sentAt 미기록
    }

    @Test
    void 이미_발송한_결제예정일은_건너뜀() {
        given(subscriptionRepository.findActiveForBillingReminder())
                .willReturn(List.of(dueSubscription()));
        given(userNotificationRepository.existsByUserIdAndSubscriptionIdAndTypeAndTargetDate(
                eq(USER_ID), eq(SUB_ID), eq(NotificationType.BILLING_REMINDER), any()))
                .willReturn(true);

        dispatchService.dispatchDueReminders(TODAY);

        verify(userNotificationRepository, never()).saveAndFlush(any());
        verify(pushSender, never()).send(anyString(), any());
        verify(pushRegistrationRepository, never()).findByUserIdAndEnabledTrue(anyLong());
    }
}
