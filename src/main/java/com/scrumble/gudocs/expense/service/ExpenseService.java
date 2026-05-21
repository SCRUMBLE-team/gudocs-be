package com.scrumble.gudocs.expense.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import com.scrumble.gudocs.expense.dto.response.CategoryExpenseItem;
import com.scrumble.gudocs.expense.dto.response.CategoryExpenseResponse;
import com.scrumble.gudocs.expense.dto.response.ExpenseTrendResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseDetailResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyTrendItem;
import com.scrumble.gudocs.expense.dto.response.SubscriptionExpenseDetail;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final int TREND_MONTHS = 6;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MonthlyExpenseResponse getMonthlyExpense(String email, int year, int month) {
        YearMonth target = parseYearMonth(year, month);
        List<Subscription> all = loadAllSubscriptions(email);

        List<Subscription> currentMonth = filterByMonth(all, target);
        List<Subscription> previousMonth = filterByMonth(all, target.minusMonths(1));

        long totalAmount = sumMonthlyAmount(currentMonth);
        long previousAmount = sumMonthlyAmount(previousMonth);
        long changeAmount = totalAmount - previousAmount;
        double changeRate = calculateChangeRate(totalAmount, previousAmount);

        long monthly = sumMonthlyAmount(filterByCycle(currentMonth, BillingCycle.MONTHLY));
        long yearlyConverted = sumMonthlyAmount(filterByCycle(currentMonth, BillingCycle.YEARLY));

        return new MonthlyExpenseResponse(
                target.getYear(), target.getMonthValue(),
                totalAmount, previousAmount, changeAmount, changeRate,
                monthly, yearlyConverted
        );
    }

    @Transactional(readOnly = true)
    public CategoryExpenseResponse getCategoryExpense(String email, int year, int month) {
        YearMonth target = parseYearMonth(year, month);
        List<Subscription> currentMonth = filterByMonth(loadAllSubscriptions(email), target);
        long totalAmount = sumMonthlyAmount(currentMonth);

        Map<SubscriptionCategory, List<Subscription>> grouped = currentMonth.stream()
                .collect(Collectors.groupingBy(Subscription::getCategory));

        List<CategoryExpenseItem> categories = grouped.entrySet().stream()
                .map(entry -> {
                    long amount = sumMonthlyAmount(entry.getValue());
                    double ratio = calculateRatio(amount, totalAmount);
                    return new CategoryExpenseItem(
                            entry.getKey(),
                            entry.getKey().getDisplayName(),
                            amount,
                            ratio,
                            entry.getValue().size()
                    );
                })
                .sorted(Comparator.comparingLong(CategoryExpenseItem::amount).reversed())
                .toList();

        return new CategoryExpenseResponse(target.getYear(), target.getMonthValue(), totalAmount, categories);
    }

    @Transactional(readOnly = true)
    public ExpenseTrendResponse getExpenseTrend(String email, int baseYear, int baseMonth) {
        YearMonth base = parseYearMonth(baseYear, baseMonth);
        List<Subscription> all = loadAllSubscriptions(email);

        List<MonthlyTrendItem> trends = IntStream
                .range(0, TREND_MONTHS)
                .mapToObj(i -> base.minusMonths(TREND_MONTHS - 1L - i))
                .map(ym -> new MonthlyTrendItem(
                        ym.getYear(),
                        ym.getMonthValue(),
                        sumMonthlyAmount(filterByMonth(all, ym))
                ))
                .toList();

        return new ExpenseTrendResponse(base.getYear(), base.getMonthValue(), trends);
    }

    @Transactional(readOnly = true)
    public MonthlyExpenseDetailResponse getMonthlyExpenseDetail(String email, int year, int month) {
        YearMonth target = parseYearMonth(year, month);
        List<Subscription> currentMonth = filterByMonth(loadAllSubscriptions(email), target);
        long totalAmount = sumMonthlyAmount(currentMonth);

        List<SubscriptionExpenseDetail> details = currentMonth.stream()
                .sorted(Comparator.comparingLong((Subscription s) -> monthlyAmount(s)).reversed())
                .map(s -> new SubscriptionExpenseDetail(
                        s.getId(),
                        s.getServiceName(),
                        s.getCategory(),
                        s.getCategory().getDisplayName(),
                        s.getBillingCycle(),
                        s.getPrice(),
                        monthlyAmount(s),
                        s.getBillingDay(),
                        s.getBillingMonth(),
                        s.getPaymentMethod(),
                        s.getStatus()
                ))
                .toList();

        return new MonthlyExpenseDetailResponse(target.getYear(), target.getMonthValue(), totalAmount, details);
    }

    private List<Subscription> loadAllSubscriptions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return subscriptionRepository.findAllByUserIncludingDeleted(user);
    }

    /**
     * 해당 월에 결제가 발생했다고 간주할 수 있는 구독만 반환.
     * - createdAt: 해당 월 말일 이전에 생성됨
     * - deletedAt: null 이거나 해당 월 1일 이후에 삭제됨
     * - pausedAt:  현재 ACTIVE 이거나, PAUSED 라도 해당 월 1일 이후에 정지됨
     */
    private List<Subscription> filterByMonth(List<Subscription> subscriptions, YearMonth yearMonth) {
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59, 999_999_999);

        return subscriptions.stream()
                .filter(s -> s.getCreatedAt() != null && !s.getCreatedAt().isAfter(endOfMonth))
                .filter(s -> s.getDeletedAt() == null || !s.getDeletedAt().isBefore(startOfMonth))
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE ||
                        (s.getStatus() == SubscriptionStatus.PAUSED &&
                         s.getPausedAt() != null && !s.getPausedAt().isBefore(startOfMonth)))
                .toList();
    }

    private List<Subscription> filterByCycle(List<Subscription> subscriptions, BillingCycle cycle) {
        return subscriptions.stream().filter(s -> s.getBillingCycle() == cycle).toList();
    }

    private long sumMonthlyAmount(List<Subscription> subscriptions) {
        return subscriptions.stream().mapToLong(this::monthlyAmount).sum();
    }

    private long monthlyAmount(Subscription s) {
        return s.getBillingCycle() == BillingCycle.MONTHLY ? s.getPrice() : s.getPrice() / 12;
    }

    private double calculateChangeRate(long current, long previous) {
        if (previous == 0) {
            return 0.0;
        }
        double rate = (double) (current - previous) / previous * 100.0;
        return Math.round(rate * 100.0) / 100.0;
    }

    private double calculateRatio(long amount, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) amount / total * 100.0 * 100.0) / 100.0;
    }

    private YearMonth parseYearMonth(int year, int month) {
        if (month < 1 || month > 12 || year < 1) {
            throw new BusinessException(ErrorCode.INVALID_YEAR_MONTH);
        }
        return YearMonth.of(year, month);
    }
}
