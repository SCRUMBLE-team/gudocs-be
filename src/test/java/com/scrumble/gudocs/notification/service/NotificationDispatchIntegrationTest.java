package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushResult;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
    void 활성_미삭제_7일이내_구독만_대상이며_활성_FID에만_발송() {
        saveSub("Netflix-due", SubscriptionStatus.ACTIVE, TODAY, false);          // 대상 O
        saveSub("Paused-due", SubscriptionStatus.PAUSED, TODAY, false);           // PAUSED 제외
        saveSub("Deleted-due", SubscriptionStatus.ACTIVE, TODAY, true);           // 삭제 제외
        saveSub("Later", SubscriptionStatus.ACTIVE, TODAY.plusDays(10), false);   // 7일 밖 제외
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
    void 동일_결제예정일_중복_발송_방지() {
        saveSub("Netflix-due", SubscriptionStatus.ACTIVE, TODAY, false);
        saveRegistration("fid-enabled", true);

        dispatchService.dispatchDueReminders(TODAY);
        dispatchService.dispatchDueReminders(TODAY); // 재실행

        assertThat(userNotificationRepository.count()).isEqualTo(1);
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }
}
