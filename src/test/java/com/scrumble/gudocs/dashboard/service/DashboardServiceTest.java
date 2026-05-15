package com.scrumble.gudocs.dashboard.service;

import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        return User.builder().id(1L).name("테스터").email("test@example.com").passwordHash("hashed").build();
    }

    private Subscription monthly(String name, SubscriptionCategory category, long price, int billingDay) {
        return Subscription.builder()
                .user(user())
                .serviceName(name)
                .category(category)
                .price(price)
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(billingDay)
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
                .billingDay(billingDay)
                .billingMonth(billingMonth)
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    private void setupUser(User u, List<Subscription> subs) {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(u));
        given(subscriptionRepository.findAllByUserOrderByCreatedAtDesc(u)).willReturn(subs);
    }

    // ─── activeSubscriptionCount ────────────────────────────────────────────────

    @Test
    void ACTIVE_구독만_카운트() {
        User u = user();
        Subscription active = monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15);
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Spotify").category(SubscriptionCategory.MUSIC)
                .price(10000L).billingCycle(BillingCycle.MONTHLY).billingDay(10)
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(active, paused));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.activeSubscriptionCount()).isEqualTo(1);
    }

    // ─── monthlyTotalExpense ─────────────────────────────────────────────────────

    @Test
    void 월간_구독_price_전액_반영() {
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.monthlyTotalExpense()).isEqualTo(17000L);
    }

    @Test
    void 연간_구독_price_12_나눈_값_반영() {
        User u = user();
        setupUser(u, List.of(yearly("Adobe", SubscriptionCategory.DESIGN, 120000L, 1, 3)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.monthlyTotalExpense()).isEqualTo(10000L);
    }

    @Test
    void PAUSED_구독은_지출에_미포함() {
        User u = user();
        Subscription active = monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15);
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Spotify").category(SubscriptionCategory.MUSIC)
                .price(10000L).billingCycle(BillingCycle.MONTHLY).billingDay(10)
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(active, paused));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

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

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

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

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

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

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.recentSubscriptions()).hasSize(3);
    }

    @Test
    void 구독_없으면_최근_구독_빈_목록() {
        User u = user();
        setupUser(u, List.of());

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.recentSubscriptions()).isEmpty();
    }

    // ─── upcomingNotifications ──────────────────────────────────────────────────

    @Test
    void 결제일_7일_이내_알림_포함() {
        // today = 2026-05-11, billingDay = 15 → nextBillingDate = 2026-05-15 (4일 후)
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).hasSize(1);
        assertThat(response.upcomingNotifications().get(0).daysUntilBilling()).isEqualTo(4);
        assertThat(response.upcomingNotifications().get(0).nextBillingDate())
                .isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void 결제일_당일_알림_포함() {
        // today = 2026-05-11, billingDay = 11 → 0일 후
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 11)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).hasSize(1);
        assertThat(response.upcomingNotifications().get(0).daysUntilBilling()).isZero();
    }

    @Test
    void 결제일_7일_후_알림_포함() {
        // today = 2026-05-11, billingDay = 18 → 2026-05-18 (7일 후, 경계값)
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 18)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).hasSize(1);
        assertThat(response.upcomingNotifications().get(0).daysUntilBilling()).isEqualTo(7);
    }

    @Test
    void 결제일_8일_후_알림_미포함() {
        // today = 2026-05-11, billingDay = 19 → 2026-05-19 (8일 후)
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 19)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).isEmpty();
    }

    @Test
    void 결제일_이미_지난_경우_다음달_계산() {
        // today = 2026-05-11, billingDay = 5 → 이번달 5일은 지남 → next: 2026-06-05 (25일 후)
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 5)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).isEmpty();
    }

    @Test
    void 결제일_31일_없는_달_마지막_날_계산() {
        // today = 2026-04-25, billingDay = 31, 4월은 30일까지 → 2026-04-30 (5일 후)
        User u = user();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(u));
        given(subscriptionRepository.findAllByUserOrderByCreatedAtDesc(u))
                .willReturn(List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 31)));

        LocalDate aprilDate = LocalDate.of(2026, 4, 25);
        DashboardResponse response = dashboardService.getDashboard("test@example.com", aprilDate);

        assertThat(response.upcomingNotifications()).hasSize(1);
        assertThat(response.upcomingNotifications().get(0).nextBillingDate())
                .isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void PAUSED_구독은_알림_미포함() {
        User u = user();
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Netflix").category(SubscriptionCategory.OTT)
                .price(17000L).billingCycle(BillingCycle.MONTHLY).billingDay(15)
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(paused));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).isEmpty();
    }

    @Test
    void 연간_구독_결제_예정일_계산() {
        // today = 2026-05-11, billingMonth = 5, billingDay = 14 → 2026-05-14 (3일 후)
        User u = user();
        setupUser(u, List.of(yearly("Adobe", SubscriptionCategory.DESIGN, 120000L, 14, 5)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).hasSize(1);
        assertThat(response.upcomingNotifications().get(0).nextBillingDate())
                .isEqualTo(LocalDate.of(2026, 5, 14));
    }

    @Test
    void 연간_구독_결제일_지나면_내년으로_계산() {
        // today = 2026-05-11, billingMonth = 3, billingDay = 1 → 2026-03-01 지남 → 2027-03-01
        User u = user();
        setupUser(u, List.of(yearly("Adobe", SubscriptionCategory.DESIGN, 120000L, 1, 3)));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).isEmpty();
    }

    @Test
    void 알림_결제일_오름차순_정렬() {
        // today = 2026-05-11
        // billingDay=15 → 05-15 (4일), billingDay=13 → 05-13 (2일)
        User u = user();
        setupUser(u, List.of(
                monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15),
                monthly("Spotify", SubscriptionCategory.MUSIC, 10000L, 13)
        ));

        DashboardResponse response = dashboardService.getDashboard("test@example.com", TODAY);

        assertThat(response.upcomingNotifications()).hasSize(2);
        assertThat(response.upcomingNotifications().get(0).serviceName()).isEqualTo("Spotify");
        assertThat(response.upcomingNotifications().get(1).serviceName()).isEqualTo("Netflix");
    }
}
