package com.scrumble.gudocs.users.service;

import com.scrumble.gudocs.billing.repository.BillingRecordRepository;
import com.scrumble.gudocs.billing.service.BillingRecordService;
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import com.scrumble.gudocs.notification.service.PushRegistrationService;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserAccountDeletionTest {

    @Autowired
    private UserService userService;
    @Autowired
    private PushRegistrationService pushRegistrationService;
    @Autowired
    private PushRegistrationRepository pushRegistrationRepository;
    @Autowired
    private UserNotificationRepository userNotificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private BillingRecordRepository billingRecordRepository;
    @Autowired
    private BillingRecordService billingRecordService;
    @Autowired
    private EntityManager entityManager;

    @Test
    void 회원_탈퇴시_FID와_알림이력이_함께_삭제된다() {
        User user = TestSessions.createUser(userRepository, socialAccountRepository, "테스터", "del@example.com");
        pushRegistrationService.register(user.getId(),
                new PushRegistrationRequest("fid-del", PushPlatform.WEB, "Chrome"));
        userNotificationRepository.save(UserNotification.builder()
                .userId(user.getId()).subscriptionId(999L)
                .type(NotificationType.BILLING_REMINDER)
                .title("t").body("b").targetDate(LocalDate.now())
                .build());

        userService.deleteAccount(user.getId());

        assertThat(pushRegistrationRepository.findByFid("fid-del")).isEmpty();
        assertThat(userNotificationRepository.count()).isZero();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    /**
     * {@code billing_records}·{@code user_notifications} 는 {@code user_id} 가 연관관계가 아니라 값
     * 컬럼이라 <b>FK cascade 가 걸리지 않는다.</b> 명시적으로 지우지 않으면 사용자를 지워도 주인 없는
     * 행으로 남고, 그 행들은 어떤 화면에서도 보이지 않아 남아 있다는 사실조차 드러나지 않는다.
     */
    @Test
    void 회원_탈퇴시_구독과_결제_기록이_함께_삭제된다() {
        User user = TestSessions.createUser(userRepository, socialAccountRepository, "테스터", "del2@example.com");
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .user(user).serviceName("Netflix").serviceCode("NETFLIX")
                .category(SubscriptionCategory.OTT).price(17000L)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.now().minusMonths(2))
                .build());
        billingRecordService.backfillPastBillings(subscription, LocalDate.now());
        assertThat(billingRecordRepository.count()).isPositive();   // 지울 대상이 실제로 있었는지 먼저 확인

        // 운영에서는 요청마다 영속성 컨텍스트가 따로지만 이 테스트는 @Transactional 로 하나를 공유한다.
        // 구독을 관리 상태로 둔 채 탈퇴하면, 벌크 삭제(JPQL)가 컨텍스트에서 그 엔티티를 걷어내지 않아
        // 이후 flush 때 이미 지워진 User 를 참조하다 TransientObjectException 이 난다. 실제 동작과
        // 무관한 테스트 인공물이므로 여기서 컨텍스트를 비워 운영과 같은 조건으로 맞춘다.
        entityManager.flush();
        entityManager.clear();

        userService.deleteAccount(user.getId());

        assertThat(billingRecordRepository.count()).isZero();
        assertThat(subscriptionRepository.findById(subscription.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
