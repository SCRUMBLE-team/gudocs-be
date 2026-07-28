package com.scrumble.gudocs.dashboard.service;

import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.dashboard.dto.InspectionResponse;
import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    // 고정 날짜: 2026-05-11 (월요일)
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User user() {
        return User.builder().id(1L).name("테스터").email("test@example.com").build();
    }

    private Subscription monthly(String name, SubscriptionCategory category, long price, int billingDay) {
        return Subscription.builder()
                .user(user())
                .serviceName(name)
                .category(category)
                .price(price)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.of(2025, 1, billingDay))
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    private Subscription yearly(String name, SubscriptionCategory category, long price, int billingDay, int billingMonth) {
        return Subscription.builder()
                .user(user())
                .serviceName(name)
                .category(category)
                .price(price)
                .billingCycle(BillingCycle.YEARLY)
                .firstBillingDate(LocalDate.of(2025, billingMonth, billingDay))
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    private void setupUser(User u, List<Subscription> subs) {
        given(userRepository.findById(1L)).willReturn(Optional.of(u));
        given(subscriptionRepository.findAllByUserOrderByCreatedAtDesc(u)).willReturn(subs);
    }

    // ─── activeSubscriptionCount ────────────────────────────────────────────────

    @Test
    void ACTIVE_구독만_카운트() {
        User u = user();
        Subscription active = monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15);
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Spotify").category(SubscriptionCategory.MUSIC)
                .price(10000L).billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 10))
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(active, paused));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.activeSubscriptionCount()).isEqualTo(1);
    }

    // ─── monthlyTotalExpense ─────────────────────────────────────────────────────

    @Test
    void 월간_구독_price_전액_반영() {
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.monthlyTotalExpense()).isEqualTo(17000L);
    }

    @Test
    void 연간_구독_price_12_나눈_값_반영() {
        User u = user();
        setupUser(u, List.of(yearly("Adobe", SubscriptionCategory.DESIGN, 120000L, 1, 3)));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.monthlyTotalExpense()).isEqualTo(10000L);
    }

    @Test
    void PAUSED_구독은_지출에_미포함() {
        User u = user();
        Subscription active = monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15);
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Spotify").category(SubscriptionCategory.MUSIC)
                .price(10000L).billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 10))
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(active, paused));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.monthlyTotalExpense()).isEqualTo(17000L);
    }

    // ─── categorySummaries ──────────────────────────────────────────────────────

    @Test
    void 카테고리별_지출_계산() {
        User u = user();
        setupUser(u, List.of(
                monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15),
                monthly("Spotify", SubscriptionCategory.MUSIC, 10000L, 5)
        ));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.categorySummaries()).hasSize(2);
        assertThat(response.categorySummaries().get(0).monthlyAmount()).isEqualTo(17000L);
        assertThat(response.categorySummaries().get(1).monthlyAmount()).isEqualTo(10000L);
    }

    @Test
    void 카테고리_비율_소수점_둘째_자리_반올림() {
        User u = user();
        // OTT: 17000 / 27000 * 100 = 62.96...% → 62.96
        // MUSIC: 10000 / 27000 * 100 = 37.03...% → 37.04
        setupUser(u, List.of(
                monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15),
                monthly("Spotify", SubscriptionCategory.MUSIC, 10000L, 5)
        ));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        double ottRatio = response.categorySummaries().stream()
                .filter(c -> c.category() == SubscriptionCategory.OTT)
                .findFirst().orElseThrow().ratio();
        assertThat(ottRatio).isEqualTo(62.96);
    }

    // ─── recentSubscriptions ────────────────────────────────────────────────────

    @Test
    void 최근_구독_최대_3개() {
        User u = user();
        setupUser(u, List.of(
                monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15),
                monthly("Spotify", SubscriptionCategory.MUSIC, 10000L, 5),
                monthly("YouTube", SubscriptionCategory.OTT, 14900L, 20),
                monthly("iCloud", SubscriptionCategory.CLOUD, 1100L, 25)
        ));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.recentSubscriptions()).hasSize(3);
    }

    @Test
    void 최근_구독_nextBillingDate_채워짐() {
        User u = user();
        // today = 2026-05-11, billingDay=15 → 2026-05-15
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.recentSubscriptions().get(0).nextBillingDate())
                .isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void 구독_없으면_최근_구독_빈_목록() {
        User u = user();
        setupUser(u, List.of());

        DashboardResponse response = dashboardService.getDashboard(1L, TODAY);

        assertThat(response.recentSubscriptions()).isEmpty();
    }

    // ─── getInspection: unusedCandidates ───────────────────────────────────────

    @Test
    void 미사용_후보_updatedAt_6개월_전이면_포함() {
        User u = user();
        Subscription old = monthly("Adobe", SubscriptionCategory.DESIGN, 24000L, 10);
        ReflectionTestUtils.setField(old, "updatedAt", TODAY.minusMonths(6).atStartOfDay());
        setupUser(u, List.of(old));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).hasSize(1);
        assertThat(response.unusedCandidates().get(0).serviceName()).isEqualTo("Adobe");
    }

    @Test
    void 미사용_후보_updatedAt_6개월에서_하루_모자라면_제외() {
        User u = user();
        Subscription recent = monthly("Adobe", SubscriptionCategory.DESIGN, 24000L, 10);
        ReflectionTestUtils.setField(recent, "updatedAt", TODAY.minusMonths(6).plusDays(1).atStartOfDay());
        setupUser(u, List.of(recent));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).isEmpty();
    }

    @Test
    void PAUSED_구독은_미사용_후보에서_제외() {
        User u = user();
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Spotify").category(SubscriptionCategory.MUSIC)
                .price(10000L).billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 10))
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        ReflectionTestUtils.setField(paused, "updatedAt", TODAY.minusMonths(12).atStartOfDay());
        setupUser(u, List.of(paused));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).isEmpty();
    }

    @Test
    void 미사용_후보_monthlyAmount는_YEARLY_12로_나눈_값() {
        User u = user();
        Subscription old = yearly("Adobe", SubscriptionCategory.DESIGN, 120000L, 1, 3);
        ReflectionTestUtils.setField(old, "updatedAt", TODAY.minusMonths(7).atStartOfDay());
        setupUser(u, List.of(old));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates().get(0).monthlyAmount()).isEqualTo(10000L);
    }

    // ─── getInspection: duplicateGroups ────────────────────────────────────────

    @Test
    void 같은_카테고리_2개_이상이면_중복_후보() {
        User u = user();
        setupUser(u, List.of(
                monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15),
                monthly("Watcha", SubscriptionCategory.OTT, 12900L, 5)
        ));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).hasSize(1);
        assertThat(response.duplicateGroups().get(0).category()).isEqualTo(SubscriptionCategory.OTT);
        assertThat(response.duplicateGroups().get(0).subscriptions()).hasSize(2);
    }

    @Test
    void 같은_카테고리_1개면_중복_후보_아님() {
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).isEmpty();
    }

    @Test
    void ETC_카테고리는_2개_이상이어도_중복_후보_제외() {
        User u = user();
        setupUser(u, List.of(
                monthly("A", SubscriptionCategory.ETC, 5000L, 15),
                monthly("B", SubscriptionCategory.ETC, 3000L, 5)
        ));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).isEmpty();
    }

    @Test
    void PAUSED_구독은_중복_후보에서_제외() {
        User u = user();
        Subscription active = monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15);
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Watcha").category(SubscriptionCategory.OTT)
                .price(12900L).billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 5))
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(active, paused));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).isEmpty();
    }

    // ─── getInspection: 후보 없음 ─────────────────────────────────────────────────

    @Test
    void 후보_없으면_양쪽_다_빈_배열() {
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).isEmpty();
        assertThat(response.duplicateGroups()).isEmpty();
    }
}
