package com.scrumble.gudocs.billing.service;

import com.scrumble.gudocs.billing.entity.BillingRecord;
import com.scrumble.gudocs.billing.repository.BillingRecordRepository;
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionPausePeriod;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionPausePeriodRepository;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BillingRecordServiceTest {

    @Autowired private BillingRecordService billingRecordService;
    @Autowired private BillingRecordRepository billingRecordRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPausePeriodRepository pausePeriodRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = TestSessions.createUser(userRepository, socialAccountRepository, "테스터", "billing@example.com");
    }

    private Subscription 구독(long price, BillingCycle cycle, LocalDate firstBillingDate, SubscriptionStatus status) {
        return subscriptionRepository.save(Subscription.builder()
                .user(user).serviceName("Netflix").serviceCode("NETFLIX")
                .category(SubscriptionCategory.OTT).price(price)
                .billingCycle(cycle).firstBillingDate(firstBillingDate)
                .status(status)
                .build());
    }

    private List<BillingRecord> 기록() {
        return billingRecordRepository.findByUserIdAndBillingDateBetween(
                user.getId(), LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1));
    }

    @Test
    void 결제일에_기록이_생긴다() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.ACTIVE);

        billingRecordService.recordDueBillings(today);

        assertThat(기록()).singleElement().satisfies(r -> {
            assertThat(r.getBillingDate()).isEqualTo(today);
            assertThat(r.getAmount()).isEqualTo(17000L);
            assertThat(r.getServiceCode()).isEqualTo("NETFLIX");
        });
    }

    @Test
    void 결제일이_아니면_기록이_생기지_않는다() {
        구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.ACTIVE);

        billingRecordService.recordDueBillings(LocalDate.of(2026, 8, 20));

        assertThat(기록()).isEmpty();
    }

    /** 배치가 같은 날 두 번 돌아도(재시작·수동 재실행) 결제가 두 번 잡히면 안 된다. */
    @Test
    void 같은_날_두_번_실행해도_한_건만_남는다() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.ACTIVE);

        billingRecordService.recordDueBillings(today);
        billingRecordService.recordDueBillings(today);

        assertThat(기록()).hasSize(1);
    }

    /**
     * 정지 중인 구독은 결제되지 않는다 → <b>행이 아예 안 생기고, 그래서 그 달 지출이 0이 된다.</b>
     */
    @Test
    void 정지된_구독은_기록되지_않는다() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.PAUSED);

        billingRecordService.recordDueBillings(today);

        assertThat(기록()).isEmpty();
    }

    /**
     * 정지 중에 결제일이 지나고 LOOKBACK_DAYS 안에 재개하면, 재개 후의 배치가 "지금 ACTIVE" 만 보고
     * 정지 중이던 날짜를 결제로 만들어 버렸다. 일어나지 않은 결제라 정지 구간으로 걸러야 한다.
     */
    @Test
    void 정지_중이던_날짜는_재개_후_배치가_거슬러_올라가도_기록하지_않는다() {
        Subscription subscription =
                구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.ACTIVE);

        // 8/14 정지 → 8/15 결제일에는 기록되지 않는다
        pausePeriodRepository.save(SubscriptionPausePeriod.open(
                subscription.getId(), LocalDateTime.of(2026, 8, 14, 9, 0)));
        subscription.updateStatus(SubscriptionStatus.PAUSED);
        billingRecordService.recordDueBillings(LocalDate.of(2026, 8, 15));
        assertThat(기록()).isEmpty();

        // 8/16 재개 → 같은 날 배치가 8/13~8/16 을 다시 훑는다
        pausePeriodRepository.findBySubscriptionIdAndEndedAtIsNull(subscription.getId())
                .orElseThrow()
                .close(LocalDateTime.of(2026, 8, 16, 10, 0));
        subscription.updateStatus(SubscriptionStatus.ACTIVE);

        billingRecordService.recordDueBillings(LocalDate.of(2026, 8, 16));

        assertThat(기록()).isEmpty();
    }

    /** 반대로 결제가 나간 뒤에 정지했다면 그 결제는 실제로 일어났으므로 기록이 남는다. */
    @Test
    void 결제일_이후에_정지했으면_그_결제는_기록된다() {
        Subscription subscription =
                구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.ACTIVE);

        // 8/16 정지 — 8/15 결제는 이미 나간 뒤다
        pausePeriodRepository.save(SubscriptionPausePeriod.open(
                subscription.getId(), LocalDateTime.of(2026, 8, 16, 9, 0)));

        billingRecordService.recordDueBillings(LocalDate.of(2026, 8, 16));

        assertThat(기록()).singleElement()
                .satisfies(r -> assertThat(r.getBillingDate()).isEqualTo(LocalDate.of(2026, 8, 15)));
    }

    @Test
    void 삭제된_구독은_기록되지_않는다() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        Subscription subscription =
                구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.ACTIVE);
        subscription.softDelete();

        billingRecordService.recordDueBillings(today);

        assertThat(기록()).isEmpty();
    }

    /** 배포·장애로 배치를 며칠 걸러도 다음 실행이 그사이 결제일을 메운다. */
    @Test
    void 배치를_거른_날의_결제도_다음_실행이_메운다() {
        구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 13), SubscriptionStatus.ACTIVE);

        // 8/13 결제일에 배치가 돌지 못했고, 이틀 뒤에야 실행됐다.
        billingRecordService.recordDueBillings(LocalDate.of(2026, 8, 15));

        assertThat(기록()).singleElement()
                .satisfies(r -> assertThat(r.getBillingDate()).isEqualTo(LocalDate.of(2026, 8, 13)));
    }

    @Test
    void 과거_최초결제일로_등록하면_그동안의_결제가_채워진다() {
        LocalDate today = LocalDate.of(2026, 8, 20);
        Subscription subscription =
                구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 5, 10), SubscriptionStatus.ACTIVE);

        billingRecordService.backfillPastBillings(subscription, today);

        // 5/10, 6/10, 7/10, 8/10
        assertThat(기록()).hasSize(4)
                .extracting(BillingRecord::getBillingDate)
                .containsExactly(
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10));
    }

    @Test
    void 최초결제일이_미래면_아무것도_채우지_않는다() {
        Subscription subscription =
                구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 9, 10), SubscriptionStatus.ACTIVE);

        billingRecordService.backfillPastBillings(subscription, LocalDate.of(2026, 8, 20));

        assertThat(기록()).isEmpty();
    }

    /** 스냅샷의 핵심 성질: 기록된 뒤 구독을 고쳐도 과거 결제액·분류는 움직이지 않는다. */
    @Test
    void 구독을_수정해도_이미_기록된_결제는_바뀌지_않는다() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        Subscription subscription =
                구독(17000L, BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15), SubscriptionStatus.ACTIVE);
        billingRecordService.recordDueBillings(today);

        subscription.update("넷플릭스", "NETFLIX", SubscriptionCategory.ETC, 25000L,
                BillingCycle.MONTHLY, LocalDate.of(2026, 8, 15));

        assertThat(기록()).singleElement().satisfies(r -> {
            assertThat(r.getAmount()).isEqualTo(17000L);
            assertThat(r.getCategory()).isEqualTo(SubscriptionCategory.OTT);
            assertThat(r.getServiceName()).isEqualTo("Netflix");
        });
    }

    @Test
    void 연간_구독은_1년에_한_번만_기록된다() {
        Subscription subscription =
                구독(120000L, BillingCycle.YEARLY, LocalDate.of(2025, 3, 1), SubscriptionStatus.ACTIVE);

        billingRecordService.backfillPastBillings(subscription, LocalDate.of(2026, 8, 20));

        assertThat(기록()).hasSize(2)
                .extracting(BillingRecord::getBillingDate)
                .containsExactly(LocalDate.of(2025, 3, 1), LocalDate.of(2026, 3, 1));
    }
}
