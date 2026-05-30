package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.notification.dto.response.UpcomingNotification;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    // 고정 날짜: 2026-05-11 (월요일)
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User user() {
        return User.builder().id(1L).name("테스터").email("test@example.com").passwordHash("hashed").build();
    }

    private Subscription monthly(String name, long price, int billingDay) {
        return Subscription.builder()
                .user(user())
                .serviceName(name)
                .category(SubscriptionCategory.OTT)
                .price(price)
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(billingDay)
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    private Subscription yearly(String name, long price, int billingDay, int billingMonth) {
        return Subscription.builder()
                .user(user())
                .serviceName(name)
                .category(SubscriptionCategory.DESIGN)
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

    @Test
    void 결제일_7일_이내_알림_포함() {
        // today = 2026-05-11, billingDay = 15 → nextBillingDate = 2026-05-15 (4일 후)
        User u = user();
        setupUser(u, List.of(monthly("Netflix", 17000L, 15)));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysUntilBilling()).isEqualTo(4);
        assertThat(result.get(0).nextBillingDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void 결제일_당일_알림_포함() {
        // today = 2026-05-11, billingDay = 11 → 0일 후
        User u = user();
        setupUser(u, List.of(monthly("Netflix", 17000L, 11)));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysUntilBilling()).isZero();
    }

    @Test
    void 결제일_7일_후_알림_포함() {
        // today = 2026-05-11, billingDay = 18 → 7일 후 경계값
        User u = user();
        setupUser(u, List.of(monthly("Netflix", 17000L, 18)));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysUntilBilling()).isEqualTo(7);
    }

    @Test
    void 결제일_8일_후_알림_미포함() {
        // today = 2026-05-11, billingDay = 19 → 8일 후
        User u = user();
        setupUser(u, List.of(monthly("Netflix", 17000L, 19)));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    void 결제일_이미_지난_경우_다음달_계산() {
        // today = 2026-05-11, billingDay = 5 → 이번달 5일은 지남 → next: 2026-06-05 (25일 후)
        User u = user();
        setupUser(u, List.of(monthly("Netflix", 17000L, 5)));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    void 결제일_31일_없는_달_마지막_날_계산() {
        // today = 2026-04-25, billingDay = 31, 4월은 30일까지 → 2026-04-30 (5일 후)
        User u = user();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(u));
        given(subscriptionRepository.findAllByUserOrderByCreatedAtDesc(u))
                .willReturn(List.of(monthly("Netflix", 17000L, 31)));

        LocalDate aprilDate = LocalDate.of(2026, 4, 25);
        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", aprilDate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nextBillingDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void PAUSED_구독은_알림_미포함() {
        User u = user();
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Netflix").category(SubscriptionCategory.OTT)
                .price(17000L).billingCycle(BillingCycle.MONTHLY).billingDay(15)
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(paused));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    void 연간_구독_결제_예정일_계산() {
        // today = 2026-05-11, billingMonth = 5, billingDay = 14 → 2026-05-14 (3일 후)
        User u = user();
        setupUser(u, List.of(yearly("Adobe", 120000L, 14, 5)));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nextBillingDate()).isEqualTo(LocalDate.of(2026, 5, 14));
    }

    @Test
    void 연간_구독_결제일_지나면_내년으로_계산() {
        // today = 2026-05-11, billingMonth = 3, billingDay = 1 → 2026-03-01 지남 → 2027-03-01
        User u = user();
        setupUser(u, List.of(yearly("Adobe", 120000L, 1, 3)));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    void 알림_결제일_오름차순_정렬() {
        // today = 2026-05-11
        // billingDay=15 → 05-15 (4일), billingDay=13 → 05-13 (2일)
        User u = user();
        setupUser(u, List.of(
                monthly("Netflix", 17000L, 15),
                monthly("Spotify", 10000L, 13)
        ));

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).serviceName()).isEqualTo("Spotify");
        assertThat(result.get(1).serviceName()).isEqualTo("Netflix");
    }

    @Test
    void 구독_없으면_빈_목록() {
        User u = user();
        setupUser(u, List.of());

        List<UpcomingNotification> result = notificationService.findUpcoming("test@example.com", TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    void 사용자_없으면_USER_NOT_FOUND() {
        given(userRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.findUpcoming("missing@example.com", TODAY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
