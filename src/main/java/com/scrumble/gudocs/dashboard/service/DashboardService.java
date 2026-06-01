package com.scrumble.gudocs.dashboard.service;

import com.scrumble.gudocs.dashboard.dto.CategorySummary;
import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String email) {
        return getDashboard(email, LocalDate.now());
    }

    @Transactional(readOnly = true)
    DashboardResponse getDashboard(String email, LocalDate today) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Subscription> all = subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user);
        List<Subscription> active = all.stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .toList();

        long monthlyTotal = calculateMonthlyTotal(active);
        List<SubscriptionResponse> recent = all.stream()
                .limit(3)
                .map(s -> SubscriptionResponse.from(s, NextBillingDateCalculator.calculate(s, today)))
                .toList();
        List<CategorySummary> categories = calculateCategorySummaries(active, monthlyTotal);

        return new DashboardResponse(monthlyTotal, active.size(), recent, categories);
    }

    private long calculateMonthlyTotal(List<Subscription> subscriptions) {
        return subscriptions.stream().mapToLong(this::monthlyAmount).sum();
    }

    private long monthlyAmount(Subscription s) {
        return s.getBillingCycle() == BillingCycle.MONTHLY ? s.getPrice() : s.getPrice() / 12;
    }

    private List<CategorySummary> calculateCategorySummaries(List<Subscription> active, long total) {
        Map<SubscriptionCategory, Long> amountByCategory = active.stream()
                .collect(Collectors.groupingBy(
                        Subscription::getCategory,
                        Collectors.summingLong(this::monthlyAmount)
                ));

        return amountByCategory.entrySet().stream()
                .map(entry -> {
                    double ratio = total == 0 ? 0.0
                            : Math.round((double) entry.getValue() / total * 100 * 100.0) / 100.0;
                    return new CategorySummary(entry.getKey(), entry.getValue(), ratio);
                })
                .sorted(Comparator.comparingLong(CategorySummary::monthlyAmount).reversed())
                .toList();
    }
}
