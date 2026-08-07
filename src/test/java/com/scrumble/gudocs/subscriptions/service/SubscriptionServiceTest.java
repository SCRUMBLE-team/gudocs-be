package com.scrumble.gudocs.subscriptions.service;

import com.scrumble.gudocs.common.fixture.UserFixture;
import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Subscription testSubscription(User user) {
        return Subscription.builder()
                .user(user)
                .serviceName("Netflix")
                .category(SubscriptionCategory.OTT)
                .price(17000L)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.of(2025, 1, 15))
                
                .build();
    }

    @Test
    void 구독_등록_성공() {
        User user = UserFixture.create();
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
        );
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.save(any(Subscription.class))).willAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.create(1L, request);

        assertThat(response.serviceName()).isEqualTo("Netflix");
        assertThat(response.firstBillingDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void 구독_등록_연간결제_성공() {
        User user = UserFixture.create();
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Adobe", null, SubscriptionCategory.DESIGN, 60000L,
                BillingCycle.YEARLY, LocalDate.of(2025, 3, 1)
        );
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.save(any(Subscription.class))).willAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.create(1L, request);

        assertThat(response.firstBillingDate()).isEqualTo(LocalDate.of(2025, 3, 1));
    }

    @Test
    void 구독_수정_성공() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest(
                "Netflix Premium", null, SubscriptionCategory.OTT, 20000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 2, 20)
        );
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.update(1L, 1L, request);

        assertThat(response.serviceName()).isEqualTo("Netflix Premium");
        assertThat(response.firstBillingDate()).isEqualTo(LocalDate.of(2025, 2, 20));
    }

    @Test
    void 구독_목록_조회_성공() {
        User user = UserFixture.create();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user))
                .willReturn(List.of(testSubscription(user)));

        List<SubscriptionResponse> result = subscriptionService.getAll(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).serviceName()).isEqualTo("Netflix");
    }

    @Test
    void 구독_상세_조회_성공() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.getOne(1L, 1L);

        assertThat(response.serviceName()).isEqualTo("Netflix");
    }

    @Test
    void 구독_상세_조회_없는_구독() {
        User user = UserFixture.create();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getOne(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    @Test
    void 구독_상세_조회_다른_사용자_403() {
        User owner = UserFixture.create(1L, "주인", "owner@example.com");
        User other = UserFixture.create(2L, "타인", "other@example.com");
        Subscription subscription = testSubscription(owner);
        given(userRepository.findById(2L)).willReturn(Optional.of(other));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        assertThatThrownBy(() -> subscriptionService.getOne(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_FORBIDDEN));
    }

    @Test
    void 구독_삭제_성공() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        subscriptionService.delete(1L, 1L);

        assertThat(subscription.isDeleted()).isTrue();
        assertThat(subscription.getDeletedAt()).isNotNull();
    }

    @Test
    void 구독_상태_변경_ACTIVE에서_PAUSED로() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        SubscriptionStatusUpdateRequest request = new SubscriptionStatusUpdateRequest(SubscriptionStatus.PAUSED);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.updateStatus(1L, 1L, request);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(subscription.getPausedAt()).isNotNull();
    }

    @Test
    void 구독_상태_변경_PAUSED에서_ACTIVE로() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        subscription.updateStatus(SubscriptionStatus.PAUSED); // pausedAt 설정
        SubscriptionStatusUpdateRequest request = new SubscriptionStatusUpdateRequest(SubscriptionStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.updateStatus(1L, 1L, request);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getPausedAt()).isNull();
    }

    @Test
    void 서비스명_중복_확인_중복있음() {
        User user = UserFixture.create();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.existsByUserAndServiceNameIgnoreCaseAndDeletedAtIsNull(user, "Netflix"))
                .willReturn(true);

        boolean result = subscriptionService.isDuplicateName(1L, "Netflix");

        assertThat(result).isTrue();
    }

    @Test
    void 서비스명_중복_확인_중복없음() {
        User user = UserFixture.create();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.existsByUserAndServiceNameIgnoreCaseAndDeletedAtIsNull(user, "Spotify"))
                .willReturn(false);

        boolean result = subscriptionService.isDuplicateName(1L, "Spotify");

        assertThat(result).isFalse();
    }

    @Test
    void 구독_상태_변경_동일_상태_변경없음() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        SubscriptionStatusUpdateRequest request = new SubscriptionStatusUpdateRequest(SubscriptionStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.updateStatus(1L, 1L, request);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getPausedAt()).isNull();
    }
}
