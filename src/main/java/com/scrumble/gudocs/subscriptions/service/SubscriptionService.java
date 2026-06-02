package com.scrumble.gudocs.subscriptions.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

        Subscription subscription = Subscription.builder()
                .user(user)
                .serviceName(request.serviceName())
                .category(request.category())
                .price(request.price())
                .billingCycle(request.billingCycle())
                .billingDay(request.billingDay())
                .billingMonth(request.billingMonth())
                .paymentMethod(request.paymentMethod())
                .build();

        return toResponse(subscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAll(String email) {
        User user = findUser(email);
        LocalDate today = LocalDate.now();
        return subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(s -> SubscriptionResponse.from(s, NextBillingDateCalculator.calculate(s, today)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getOne(String email, Long subscriptionId) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse update(String email, Long subscriptionId, SubscriptionUpdateRequest request) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);

        Integer effectiveBillingMonth;
        if (request.billingCycle() == BillingCycle.MONTHLY) {
            if (request.billingMonth() != null) {
                throw new BusinessException(ErrorCode.BILLING_MONTH_NOT_ALLOWED);
            }
            effectiveBillingMonth = null;
        } else {
            effectiveBillingMonth = request.billingMonth();
            if (effectiveBillingMonth == null) {
                throw new BusinessException(ErrorCode.INVALID_BILLING_MONTH);
            }
        }

        subscription.update(
                request.serviceName(), request.category(), request.price(),
                request.billingCycle(), request.billingDay(), effectiveBillingMonth,
                request.paymentMethod()
        );

        return toResponse(subscription);
    }

    @Transactional
    public void delete(String email, Long subscriptionId) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        subscription.softDelete();
    }

    @Transactional(readOnly = true)
    public boolean isDuplicateName(String email, String serviceName) {
        User user = findUser(email);
        return subscriptionRepository.existsByUserAndServiceNameIgnoreCaseAndDeletedAtIsNull(user, serviceName);
    }

    @Transactional
    public SubscriptionResponse updateStatus(String email, Long subscriptionId,
                                             SubscriptionStatusUpdateRequest request) {
        User user = findUser(email);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        subscription.updateStatus(request.status());
        return toResponse(subscription);
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return SubscriptionResponse.from(
                subscription,
                NextBillingDateCalculator.calculate(subscription, LocalDate.now())
        );
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Subscription findSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (subscription.isDeleted()) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        return subscription;
    }

    private void checkOwnership(Subscription subscription, User user) {
        if (!Objects.equals(subscription.getUser().getId(), user.getId())) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_FORBIDDEN);
        }
    }
}
