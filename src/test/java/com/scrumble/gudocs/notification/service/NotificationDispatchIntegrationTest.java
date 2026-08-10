package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushResult;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class NotificationDispatchIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);

    @Autowired
    private NotificationDispatchService dispatchService;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private UserNotificationRepository userNotificationRepository;
    @Autowired
    private com.scrumble.gudocs.notification.repository.PushRegistrationRepository pushRegistrationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;

    // 실제 발송 대신 Mock — DB 상호작용만 검증
    @MockBean
    private PushSender pushSender;

    private User user;

    @BeforeEach
    void setUp() {
        user = TestSessions.createUser(userRepository, socialAccountRepository, "테스터", "dispatch@example.com");
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.SUCCESS);
    }

    private Subscription saveSub(String name, SubscriptionStatus status, LocalDate firstBillingDate, boolean deleted) {
        Subscription sub = Subscription.builder()
                .user(user).serviceName(name).category(SubscriptionCategory.OTT)
                .price(17000L).billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(firstBillingDate).status(status)
                .build();
        if (deleted) {
            sub.softDelete();
        }
        return subscriptionRepository.save(sub);
    }

    private void saveRegistration(String fid, boolean enabled) {
        PushRegistration reg = PushRegistration.builder()
                .user(user).fid(fid).platform(PushPlatform.WEB).enabled(enabled)
                .lastRegisteredAt(LocalDateTime.now())
                .build();
        pushRegistrationRepository.save(reg);
    }

    @Test
    void 활성_미삭제_당일_구독만_대상이며_활성_FID에만_발송() {
        saveSub("Netflix-due", SubscriptionStatus.ACTIVE, TODAY, false);          // 당일(D-0) 대상 O
        saveSub("Paused-due", SubscriptionStatus.PAUSED, TODAY, false);           // PAUSED 제외
        saveSub("Deleted-due", SubscriptionStatus.ACTIVE, TODAY, true);           // 삭제 제외
        saveSub("Later", SubscriptionStatus.ACTIVE, TODAY.plusDays(10), false);   // D-10 제외
        saveRegistration("fid-enabled", true);
        saveRegistration("fid-disabled", false);

        dispatchService.dispatchDueReminders(TODAY);

        // 대상 구독 1건 → UserNotification 1건
        assertThat(userNotificationRepository.count()).isEqualTo(1);
        // 활성 FID에만 발송
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
        verify(pushSender).send(org.mockito.ArgumentMatchers.eq("fid-enabled"), any(PushMessage.class));
    }

    @Test
    void D3_구독은_대상이고_D7은_제외() {
        saveSub("Netflix-D3", SubscriptionStatus.ACTIVE, TODAY.plusDays(3), false); // D-3 대상 O
        saveSub("Spotify-D7", SubscriptionStatus.ACTIVE, TODAY.plusDays(7), false); // D-7 제외
        saveRegistration("fid-enabled", true);

        dispatchService.dispatchDueReminders(TODAY);

        assertThat(userNotificationRepository.count()).isEqualTo(1);
        UserNotification saved = userNotificationRepository.findAll().get(0);
        assertThat(saved.getRemindOffset()).isEqualTo(3);
        // 묶음 알림 → 특정 구독 없음. NULL 이 아니라 sentinel 인 이유는 MySQL UNIQUE 가 NULL 을
        // 서로 다른 값으로 봐서, dedup 키에 subscription_id 를 넣는 순간 중복 방지가 풀리기 때문이다.
        assertThat(saved.getSubscriptionId()).isEqualTo(UserNotification.NO_SUBSCRIPTION);
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 같은_결제일_여러_구독은_한건으로_묶여_발송() {
        saveSub("Netflix", SubscriptionStatus.ACTIVE, TODAY, false);
        saveSub("Spotify", SubscriptionStatus.ACTIVE, TODAY, false);
        saveSub("YouTube", SubscriptionStatus.ACTIVE, TODAY, false);
        saveRegistration("fid-enabled", true);

        dispatchService.dispatchDueReminders(TODAY);

        // 3개 구독이 같은 결제일 → 알림 1건, 발송 1회
        assertThat(userNotificationRepository.count()).isEqualTo(1);
        UserNotification saved = userNotificationRepository.findAll().get(0);
        assertThat(saved.getBody()).contains("3건");
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 결제일이_다르면_각각_별도_알림() {
        saveSub("Netflix-today", SubscriptionStatus.ACTIVE, TODAY, false);          // D-0
        saveSub("Spotify-D3", SubscriptionStatus.ACTIVE, TODAY.plusDays(3), false); // D-3
        saveRegistration("fid-enabled", true);

        dispatchService.dispatchDueReminders(TODAY);

        // 결제일이 다르므로 묶이지 않고 2건
        assertThat(userNotificationRepository.count()).isEqualTo(2);
        verify(pushSender, times(2)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 절약_후보_D3는_구독별_해지_알림으로_남고_재실행해도_중복되지_않는다() {
        Subscription netflix = saveSub("Netflix-D3", SubscriptionStatus.ACTIVE, TODAY.plusDays(3), false);
        Subscription spotify = saveSub("Spotify-D3", SubscriptionStatus.ACTIVE, TODAY.plusDays(3), false);
        netflix.updateSavingsSelection(true);
        spotify.updateSavingsSelection(true);
        subscriptionRepository.saveAll(List.of(netflix, spotify));
        saveRegistration("fid-enabled", true);

        dispatchService.dispatchDueReminders(TODAY);

        // 같은 날 같은 단계라도 구독별로 1건씩 — dedup 키에 subscription_id 가 들어가기 때문이다.
        assertThat(userNotificationRepository.findAll())
                .hasSize(2)
                .allSatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.CANCEL_REMINDER))
                .extracting(UserNotification::getSubscriptionId)
                .containsExactlyInAnyOrder(netflix.getId(), spotify.getId());
        verify(pushSender, times(2)).send(anyString(), any(PushMessage.class));

        dispatchService.dispatchDueReminders(TODAY); // 재실행

        assertThat(userNotificationRepository.count()).isEqualTo(2);
        verify(pushSender, times(2)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 동일_결제예정일_중복_발송_방지() {
        saveSub("Netflix-due", SubscriptionStatus.ACTIVE, TODAY, false);
        saveRegistration("fid-enabled", true);

        dispatchService.dispatchDueReminders(TODAY);
        dispatchService.dispatchDueReminders(TODAY); // 재실행

        assertThat(userNotificationRepository.count()).isEqualTo(1);
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 활성_FID가_없으면_이력만_남고_다음_스케줄에_재발송() {
        saveSub("Netflix-due", SubscriptionStatus.ACTIVE, TODAY, false);
        // 등록된 FID 없음

        dispatchService.dispatchDueReminders(TODAY);

        // 이력은 생성되지만 발송 성공은 없음(sentAt == null) → 발송 0
        assertThat(userNotificationRepository.count()).isEqualTo(1);
        assertThat(userNotificationRepository.findAll().get(0).getSentAt()).isNull();
        verify(pushSender, never()).send(anyString(), any(PushMessage.class));

        // 이후 FID 등록 → 다음 스케줄에 기존 이력 재사용해 재발송
        saveRegistration("fid-late", true);
        dispatchService.dispatchDueReminders(TODAY);

        assertThat(userNotificationRepository.count()).isEqualTo(1); // 새 행 생성 안 함
        assertThat(userNotificationRepository.findAll().get(0).getSentAt()).isNotNull();
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 일시_장애로_실패하면_다음_스케줄에_재발송() {
        saveSub("Netflix-due", SubscriptionStatus.ACTIVE, TODAY, false);
        saveRegistration("fid-enabled", true);

        // 1차: 일시 장애
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.FAILED);
        dispatchService.dispatchDueReminders(TODAY);

        assertThat(userNotificationRepository.findAll().get(0).getSentAt()).isNull();

        // 2차: 정상 → 기존 이력 재사용해 재발송, sentAt 기록
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.SUCCESS);
        dispatchService.dispatchDueReminders(TODAY);

        assertThat(userNotificationRepository.count()).isEqualTo(1);
        assertThat(userNotificationRepository.findAll().get(0).getSentAt()).isNotNull();
        verify(pushSender, times(2)).send(anyString(), any(PushMessage.class));
    }
}
