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
                .billingDay(15)
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    @Test
    void 구독_등록_성공() {
        User user = UserFixture.create();
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.save(any(Subscription.class))).willAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.create("test@example.com", request);

        assertThat(response.serviceName()).isEqualTo("Netflix");
        assertThat(response.billingMonth()).isNull();
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void 구독_등록_연간결제_월_없음_실패() {
        User user = UserFixture.create();
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.YEARLY, 15, null, PaymentMethod.CARD
        );
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> subscriptionService.create("test@example.com", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_BILLING_MONTH));
    }

    @Test
    void 구독_등록_연간결제_월_성공() {
        User user = UserFixture.create();
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Adobe", SubscriptionCategory.DESIGN, 60000L,
                BillingCycle.YEARLY, 1, 3, PaymentMethod.CARD
        );
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.save(any(Subscription.class))).willAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.create("test@example.com", request);

        assertThat(response.billingMonth()).isEqualTo(3);
    }

    @Test
    void 구독_등록_월간결제_결제월_입력_실패() {
        User user = UserFixture.create();
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, 5, PaymentMethod.CARD
        );
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> subscriptionService.create("test@example.com", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BILLING_MONTH_NOT_ALLOWED));
    }

    @Test
    void 구독_수정_월간결제_결제월_입력_실패() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user); // MONTHLY
        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest(
                null, null, null, null, null, 5, null
        );
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        assertThatThrownBy(() -> subscriptionService.update("test@example.com", 1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BILLING_MONTH_NOT_ALLOWED));
    }

    @Test
    void 구독_목록_조회_성공() {
        User user = UserFixture.create();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user))
                .willReturn(List.of(testSubscription(user)));

        List<SubscriptionResponse> result = subscriptionService.getAll("test@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).serviceName()).isEqualTo("Netflix");
    }

    @Test
    void 구독_상세_조회_성공() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.getOne("test@example.com", 1L);

        assertThat(response.serviceName()).isEqualTo("Netflix");
    }

    @Test
    void 구독_상세_조회_없는_구독() {
        User user = UserFixture.create();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getOne("test@example.com", 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    @Test
    void 구독_상세_조회_다른_사용자_403() {
        User owner = UserFixture.create(1L, "주인", "owner@example.com");
        User other = UserFixture.create(2L, "타인", "other@example.com");
        Subscription subscription = testSubscription(owner);
        given(userRepository.findByEmail("other@example.com")).willReturn(Optional.of(other));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        assertThatThrownBy(() -> subscriptionService.getOne("other@example.com", 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_FORBIDDEN));
    }

    @Test
    void 구독_삭제_성공() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        subscriptionService.delete("test@example.com", 1L);

        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    void 구독_상태_변경_성공() {
        User user = UserFixture.create();
        Subscription subscription = testSubscription(user);
        SubscriptionStatusUpdateRequest request = new SubscriptionStatusUpdateRequest(SubscriptionStatus.PAUSED);
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.updateStatus("test@example.com", 1L, request);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.PAUSED);
    }
}
