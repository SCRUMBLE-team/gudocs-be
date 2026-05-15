package com.scrumble.gudocs.dashboard.service;

import com.scrumble.gudocs.dashboard.dto.CategorySummary;
import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.dashboard.dto.UpcomingNotification;
import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        List<SubscriptionResponse> recent = all.stream().limit(3).map(SubscriptionResponse::from).toList();
        List<CategorySummary> categories = calculateCategorySummaries(active, monthlyTotal);
        List<UpcomingNotification> upcoming = calculateUpcomingNotifications(active, today);

        return new DashboardResponse(upcoming, monthlyTotal, active.size(), recent, categories);
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

    private List<UpcomingNotification> calculateUpcomingNotifications(List<Subscription> active, LocalDate today) {
        LocalDate threshold = today.plusDays(7);

        return active.stream()
                .flatMap(s -> {
                    LocalDate nextBillingDate = calculateNextBillingDate(s, today);
                    if (!nextBillingDate.isBefore(today) && !nextBillingDate.isAfter(threshold)) {
                        int daysUntil = (int) ChronoUnit.DAYS.between(today, nextBillingDate);
                        return Stream.of(new UpcomingNotification(
                                s.getId(), s.getServiceName(), s.getPrice(), nextBillingDate, daysUntil));
                    }
                    return Stream.empty();
                })
                .sorted(Comparator.comparing(UpcomingNotification::nextBillingDate))
                .toList();
    }

    private LocalDate calculateNextBillingDate(Subscription subscription, LocalDate today) {
        if (subscription.getBillingCycle() == BillingCycle.MONTHLY) {
            return calculateMonthlyNextBillingDate(subscription.getBillingDay(), today);
        }
        return calculateYearlyNextBillingDate(subscription.getBillingDay(), subscription.getBillingMonth(), today);
    }

    private LocalDate calculateMonthlyNextBillingDate(int billingDay, LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate billingDate = currentMonth.atDay(Math.min(billingDay, currentMonth.lengthOfMonth()));

        if (!billingDate.isBefore(today)) {
            return billingDate;
        }

        YearMonth nextMonth = currentMonth.plusMonths(1);
        return nextMonth.atDay(Math.min(billingDay, nextMonth.lengthOfMonth()));
    }

    private LocalDate calculateYearlyNextBillingDate(int billingDay, int billingMonth, LocalDate today) {
        YearMonth thisYearMonth = YearMonth.of(today.getYear(), billingMonth);
        LocalDate billingDate = thisYearMonth.atDay(Math.min(billingDay, thisYearMonth.lengthOfMonth()));

        if (!billingDate.isBefore(today)) {
            return billingDate;
        }

        YearMonth nextYearMonth = YearMonth.of(today.getYear() + 1, billingMonth);
        return nextYearMonth.atDay(Math.min(billingDay, nextYearMonth.lengthOfMonth()));
    }
}
