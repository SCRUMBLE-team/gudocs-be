package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushResult;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog.DeclaredPriceChange;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class PriceChangeDispatchIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 11);
    private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 9, 1);

    @Autowired
    private PriceChangeDispatchService dispatchService;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private UserNotificationRepository userNotificationRepository;
    @Autowired
    private PushRegistrationRepository pushRegistrationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @MockBean
    private PushSender pushSender;

    private User user;

    @BeforeEach
    void setUp() {
        user = TestSessions.createUser(userRepository, socialAccountRepository, "테스터", "price@example.com");
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.SUCCESS);
    }

    /** 넷플릭스 프리미엄 17,000 → 19,000원 인상 예고. 카탈로그에 실제로 심지 않고 테스트에서만 구성한다. */
    private DeclaredPriceChange netflixPremium() {
        ServiceCatalog.CatalogService netflix = ServiceCatalog.findByCode("NETFLIX").orElseThrow();
        ServiceCatalog.Plan premium =
                new ServiceCatalog.Plan("프리미엄", 17000L, BillingCycle.MONTHLY, false, null)
                        .withPriceChange(17000L, 19000L, EFFECTIVE_ON, LocalDate.of(2026, 8, 5),
                                "https://help.netflix.com/ko/node/example");
        return new DeclaredPriceChange(netflix, premium, premium.change());
    }

    private Subscription saveSub(String code, long price, SubscriptionStatus status, boolean deleted) {
        Subscription sub = Subscription.builder()
                .user(user).serviceName("넷플릭스").serviceCode(code)
                .category(SubscriptionCategory.OTT).price(price)
                .billingCycle(BillingCycle.MONTHLY).firstBillingDate(TODAY).status(status)
                .build();
        if (deleted) {
            sub.softDelete();
        }
        return subscriptionRepository.save(sub);
    }

    private void saveRegistration(String fid) {
        pushRegistrationRepository.save(PushRegistration.builder()
                .user(user).fid(fid).platform(PushPlatform.WEB).enabled(true)
                .lastRegisteredAt(LocalDateTime.now())
                .build());
    }

    @Test
    void 해당_요금제_사용자만_알림을_받는다() {
        Subscription premium = saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, false);
        saveSub("NETFLIX", 13500L, SubscriptionStatus.ACTIVE, false);  // 스탠다드 — 대상 아님
        saveSub("NETFLIX", 17000L, SubscriptionStatus.PAUSED, false);  // 정지 — 제외
        saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, true);   // 삭제 — 제외
        saveSub("SPOTIFY", 17000L, SubscriptionStatus.ACTIVE, false);  // 다른 서비스 — 제외
        saveSub(null, 17000L, SubscriptionStatus.ACTIVE, false);       // 직접 입력 — 카탈로그에 없으므로 제외
        saveRegistration("fid-enabled");

        dispatchService.dispatch(List.of(netflixPremium()), TODAY);

        assertThat(userNotificationRepository.findAll())
                .hasSize(1)
                .allSatisfy(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.PRICE_CHANGE);
                    assertThat(n.getSubscriptionId()).isEqualTo(premium.getId());
                    // dedup 키의 target_date 는 적용 예정일이다.
                    assertThat(n.getTargetDate()).isEqualTo(EFFECTIVE_ON);
                    assertThat(n.getSentAt()).isNotNull();
                });
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 매일_배치가_돌아도_같은_인상_건은_한_번만_발송된다() {
        saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, false);
        saveRegistration("fid-enabled");

        dispatchService.dispatch(List.of(netflixPremium()), TODAY);
        dispatchService.dispatch(List.of(netflixPremium()), TODAY.plusDays(1));
        dispatchService.dispatch(List.of(netflixPremium()), TODAY.plusDays(2));

        // target_date 가 적용일로 고정돼 있어 며칠에 걸쳐 배치가 돌아도 dedup 키가 같다.
        assertThat(userNotificationRepository.count()).isEqualTo(1);
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 알림_이후_등록한_구독도_적용일_전이면_알림을_받는다() {
        saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, false);
        saveRegistration("fid-enabled");
        dispatchService.dispatch(List.of(netflixPremium()), TODAY);

        // 인상 발표를 안 뒤에 넷플릭스를 새로 등록한 사용자도 적용 전에는 알아야 한다.
        Subscription added = saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, false);
        dispatchService.dispatch(List.of(netflixPremium()), TODAY.plusDays(1));

        assertThat(userNotificationRepository.findAll())
                .hasSize(2)
                .extracting(UserNotification::getSubscriptionId)
                .contains(added.getId());
        verify(pushSender, times(2)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 발표와_금액_확인은_각각_한_번씩_나간다() {
        // 결제일 매달 5일. 9월 1일 적용 → 9월 5일 결제 → 그 뒤부터 "확인하셨나요".
        Subscription premium = subscriptionRepository.save(Subscription.builder()
                .user(user).serviceName("넷플릭스").serviceCode("NETFLIX")
                .category(SubscriptionCategory.OTT).price(17000L)
                .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2026, 5, 5))
                .status(SubscriptionStatus.ACTIVE).build());
        saveRegistration("fid-enabled");

        dispatchService.dispatch(List.of(netflixPremium()), TODAY);                        // 발표
        dispatchService.dispatch(List.of(netflixPremium()), TODAY.plusDays(1));            // 발표(중복)
        dispatchService.dispatch(List.of(netflixPremium()), LocalDate.of(2026, 9, 3));     // 아직 결제 전
        dispatchService.dispatch(List.of(netflixPremium()), LocalDate.of(2026, 9, 6));     // 결제 후 → 확인
        dispatchService.dispatch(List.of(netflixPremium()), LocalDate.of(2026, 9, 7));     // 확인(중복)

        // 같은 변경 건·같은 구독이지만 remind_offset 이 달라 두 단계가 서로를 막지 않는다.
        assertThat(userNotificationRepository.findAll())
                .hasSize(2)
                .allSatisfy(n -> assertThat(n.getSubscriptionId()).isEqualTo(premium.getId()))
                .extracting(UserNotification::getRemindOffset)
                .containsExactlyInAnyOrder(0, 1);
        verify(pushSender, times(2)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 금액을_반영한_사용자에게는_더_묻지_않는다() {
        Subscription premium = saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, false);
        saveRegistration("fid-enabled");
        dispatchService.dispatch(List.of(netflixPremium()), TODAY);
        assertThat(userNotificationRepository.count()).isEqualTo(1);

        // 사용자가 안내를 보고 19,000원으로 수정 → 더 이상 구가격과 일치하지 않는다.
        premium.update("넷플릭스", "NETFLIX", SubscriptionCategory.OTT, 19000L,
                BillingCycle.MONTHLY, premium.getFirstBillingDate());
        subscriptionRepository.saveAndFlush(premium);

        dispatchService.dispatch(List.of(netflixPremium()), LocalDate.of(2026, 9, 20));

        // "확인했음" 상태를 저장하지 않아도 대상에서 저절로 빠진다.
        assertThat(userNotificationRepository.count()).isEqualTo(1);
        verify(pushSender, times(1)).send(anyString(), any(PushMessage.class));
    }

    @Test
    void 사용자의_구독_금액은_그대로다() {
        Subscription premium = saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, false);
        saveRegistration("fid-enabled");

        dispatchService.dispatch(List.of(netflixPremium()), TODAY);

        // 알림만 보낸다. 실제 청구액은 프로모션·제휴결합·인앱결제로 사람마다 다르므로
        // 서버가 대신 고치면 지출 분석이 사실과 어긋난다 — 반영은 사용자가 직접 고른다.
        assertThat(subscriptionRepository.findById(premium.getId()).orElseThrow().getPrice())
                .isEqualTo(17000L);
    }

    @Test
    void 실제_카탈로그_선언만큼만_발송한다() {
        saveSub("NETFLIX", 17000L, SubscriptionStatus.ACTIVE, false);
        saveRegistration("fid-enabled");

        // 카탈로그를 그대로 읽는 진입점. 선언이 없는 평상시에는 0건이고, 넷플릭스 프리미엄 인상이
        // 실제로 선언되면 그만큼만 나간다(선언을 추가했다고 이 테스트가 깨지지 않게 기대값을 함께 계산).
        long expected = ServiceCatalog.declaredPriceChanges().stream()
                .filter(d -> d.service().code().equals("NETFLIX")
                        && d.plan().price() == 17000L
                        && d.plan().billingCycle() == BillingCycle.MONTHLY
                        && !d.change().effectiveOn().isBefore(TODAY))
                .count();

        dispatchService.dispatchDeclaredPriceChanges(TODAY);

        assertThat(userNotificationRepository.count()).isEqualTo(expected);
    }
}
