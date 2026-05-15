package com.scrumble.gudocs.subscriptions.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public SubscriptionResponse create(String email, SubscriptionCreateRequest request) {
        User user = findUser(email);

        if (request.billingCycle() == BillingCycle.MONTHLY && request.billingMonth() != null) {
            throw new BusinessException(ErrorCode.BILLING_MONTH_NOT_ALLOWED);
        }
        if (request.billingCycle() == BillingCycle.YEARLY && request.billingMonth() == null) {
            throw new BusinessException(ErrorCode.INVALID_BILLING_MONTH);
        }
        Integer billingMonth = request.billingCycle() == BillingCycle.MONTHLY ? null : request.billingMonth();

        Subscription subscription = Subscription.builder()
                .user(user)
                .serviceName(request.serviceName())
                .category(request.category())
                .price(request.price())
                .billingCycle(request.billingCycle())
                .billingDay(request.billingDay())
                .billingMonth(billingMonth)
                .paymentMethod(request.paymentMethod())
                .build();

        return SubscriptionResponse.from(subscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAll(String email) {
        User user = findUser(email);
        return subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getOne(String email, Long subscriptionId) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public SubscriptionResponse update(String email, Long subscriptionId, SubscriptionUpdateRequest request) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);

        BillingCycle effectiveCycle = request.billingCycle() != null
                ? request.billingCycle()
                : subscription.getBillingCycle();

        Integer effectiveBillingMonth;
        if (effectiveCycle == BillingCycle.MONTHLY) {
            if (request.billingMonth() != null) {
                throw new BusinessException(ErrorCode.BILLING_MONTH_NOT_ALLOWED);
            }
            effectiveBillingMonth = null;
        } else {
            effectiveBillingMonth = request.billingMonth() != null
                    ? request.billingMonth()
                    : subscription.getBillingMonth();
            if (effectiveBillingMonth == null) {
                throw new BusinessException(ErrorCode.INVALID_BILLING_MONTH);
            }
        }

        subscription.update(
                request.serviceName(), request.category(), request.price(),
                request.billingCycle(), request.billingDay(), effectiveBillingMonth,
                request.paymentMethod()
        );

        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public void delete(String email, Long subscriptionId) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        subscriptionRepository.delete(subscription);
    }

    @Transactional
    public SubscriptionResponse updateStatus(String email, Long subscriptionId,
                                             SubscriptionStatusUpdateRequest request) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        subscription.updateStatus(request.status());
        return SubscriptionResponse.from(subscription);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Subscription findSubscription(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    private void checkOwnership(Subscription subscription, User user) {
        if (!Objects.equals(subscription.getUser().getId(), user.getId())) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_FORBIDDEN);
        }
    }
}
