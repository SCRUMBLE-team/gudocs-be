package com.scrumble.gudocs.expense.service;

import com.scrumble.gudocs.billing.entity.BillingRecord;
import com.scrumble.gudocs.billing.repository.BillingRecordRepository;
import com.scrumble.gudocs.expense.dto.response.CategoryExpenseItem;
import com.scrumble.gudocs.expense.dto.response.CategoryExpenseResponse;
import com.scrumble.gudocs.expense.dto.response.ExpenseTrendResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseDetailResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyTrendItem;
import com.scrumble.gudocs.expense.dto.response.SubscriptionExpenseDetail;
import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionPausePeriod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionPausePeriodRepository;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 지출 분석. <b>모든 금액은 {@code billing_records}(등록정보 기반 청구 스냅샷)에서 나온다.</b>
 *
 * <p>구독 테이블에서 매번 계산하지 않는 이유는 {@link BillingRecord} 주석 참고 — 요약하면 과거 지출이
 * 나중의 가격 수정·정지·삭제에 따라 움직이면 안 되기 때문이다. 여기서 구독 테이블을 읽는 곳은
 * 상세 화면의 <b>표시용 부가정보</b>(현재 상태·삭제 여부)뿐이고, 금액에는 관여하지 않는다.
 *
 * <p>두 가지 금액을 구분해서 낸다:
 * <ul>
 *   <li><b>월평균 부담</b>({@code totalAmount} 등) — 결제액을 커버 기간에 분산. 연간 120,000원은
 *       결제월부터 12개월에 10,000원씩. 매달 평탄해서 "이 달 구독 부담"을 본다</li>
 *   <li><b>기록 청구액</b>({@code recordedBillingAmount}, JSON 호환명 {@code actualAmount}) — 그 달에
 *       청구 예정일이 도래해 저장된 금액의 합. 연간 구독은 해당 월에 전액이 잡힌다. 카드·은행에서
 *       확인한 실제 승인 금액은 아니다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final int TREND_MONTHS = 6;

    /**
     * 한 결제가 부담을 미치는 최대 개월 수(연간 = 12). 어떤 달의 부담을 알려면 그만큼 앞선 결제까지
     * 읽어야 한다 — 3월의 부담에는 작년 4월에 낸 연간 결제가 아직 남아 있다.
     */
    private static final int MAX_COVERAGE_MONTHS = 12;

    private final BillingRecordRepository billingRecordRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPausePeriodRepository pausePeriodRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MonthlyExpenseResponse getMonthlyExpense(Long userId, int year, int month) {
        User user = findUser(userId);
        YearMonth target = parseYearMonth(year, month);
        // 전월 비교가 있으므로 한 달 앞까지 함께 읽는다.
        List<BillingRecord> recorded = loadRecords(userId, target.minusMonths(1), target);
        List<BillingRecord> records = withCurrentMonthProjection(user, recorded, target.minusMonths(1), target);

        long totalAmount = burden(records, target);
        long previousAmount = burden(records, target.minusMonths(1));
        long changeAmount = totalAmount - previousAmount;
        double changeRate = calculateChangeRate(totalAmount, previousAmount);

        long monthly = burden(byCycle(records, BillingCycle.MONTHLY), target);
        long yearlyConverted = burden(byCycle(records, BillingCycle.YEARLY), target);
        // 기록 청구액에는 아직 저장되지 않은 이번 달 예정분을 섞지 않는다.
        long recordedBillingAmount = recorded.stream()
                .filter(r -> r.billedIn(target))
                .mapToLong(BillingRecord::getAmount)
                .sum();

        return new MonthlyExpenseResponse(
                target.getYear(), target.getMonthValue(),
                totalAmount, previousAmount, changeAmount, changeRate,
                monthly, yearlyConverted, recordedBillingAmount
        );
    }

    @Transactional(readOnly = true)
    public CategoryExpenseResponse getCategoryExpense(Long userId, int year, int month) {
        User user = findUser(userId);
        YearMonth target = parseYearMonth(year, month);
        List<BillingRecord> covering = covering(
                withCurrentMonthProjection(user, loadRecords(userId, target, target), target, target), target);
        long totalAmount = burden(covering, target);

        // 카테고리도 청구 스냅샷을 쓴다. 사용자가 지금 카테고리를 바꿔도 과거 분류는 그대로다.
        Map<SubscriptionCategory, List<BillingRecord>> grouped = covering.stream()
                .collect(Collectors.groupingBy(BillingRecord::getCategory));

        List<CategoryExpenseItem> categories = grouped.entrySet().stream()
                .map(entry -> {
                    long amount = burden(entry.getValue(), target);
                    return new CategoryExpenseItem(
                            entry.getKey(),
                            entry.getKey().getDisplayName(),
                            amount,
                            calculateRatio(amount, totalAmount),
                            // 같은 구독이 그 달에 두 번 잡히지 않도록 구독 기준으로 센다.
                            (int) entry.getValue().stream().map(BillingRecord::getSubscriptionId).distinct().count()
                    );
                })
                .sorted(Comparator.comparingLong(CategoryExpenseItem::amount).reversed())
                .toList();

        return new CategoryExpenseResponse(target.getYear(), target.getMonthValue(), totalAmount, categories);
    }

    @Transactional(readOnly = true)
    public ExpenseTrendResponse getExpenseTrend(Long userId, int baseYear, int baseMonth) {
        User user = findUser(userId);
        YearMonth base = parseYearMonth(baseYear, baseMonth);
        YearMonth first = base.minusMonths(TREND_MONTHS - 1L);
        List<BillingRecord> records =
                withCurrentMonthProjection(user, loadRecords(userId, first, base), first, base);

        List<MonthlyTrendItem> trends = IntStream
                .range(0, TREND_MONTHS)
                .mapToObj(i -> first.plusMonths(i))
                .map(ym -> new MonthlyTrendItem(ym.getYear(), ym.getMonthValue(), burden(records, ym)))
                .toList();

        return new ExpenseTrendResponse(base.getYear(), base.getMonthValue(), trends);
    }

    @Transactional(readOnly = true)
    public MonthlyExpenseDetailResponse getMonthlyExpenseDetail(Long userId, int year, int month) {
        User user = findUser(userId);
        YearMonth target = parseYearMonth(year, month);
        List<BillingRecord> recorded = loadRecords(userId, target, target);
        List<BillingRecord> covering = covering(
                withCurrentMonthProjection(user, recorded, target, target), target);
        long totalAmount = burden(covering, target);

        // 청구액은 저장된 기록만 센다. 부담(covering)에는 이번 달 예정분이 섞여 있는데, 예정분은
        // 아직 결제일이 오지 않은 임시 객체라 "청구됐다"고 말하면 안 된다(월별 응답의 actualAmount 와 같은 규칙).
        Map<Long, Long> billedBySubscription = recorded.stream()
                .filter(r -> r.billedIn(target))
                .collect(Collectors.groupingBy(BillingRecord::getSubscriptionId,
                        Collectors.summingLong(BillingRecord::getAmount)));

        // 반대로 "앞으로 나갈 돈"은 예정분만 센다. 저장되지 않은 임시 객체(id 없음)가 곧 예정분이고,
        // 이번 달에만 생긴다. 화면이 직접 보여주는 개념이라 서버가 답한다 — 프론트가 날짜를 비교해
        // 되짚으면 정지·삭제·연간 같은 경계에서 계속 어긋난다(실제로 정지에서 어긋났다).
        Map<Long, Long> scheduledBySubscription = covering.stream()
                .filter(r -> r.getId() == null && r.billedIn(target))
                .collect(Collectors.groupingBy(BillingRecord::getSubscriptionId,
                        Collectors.summingLong(BillingRecord::getAmount)));

        // 현재 상태·삭제 여부는 "지금 이 구독이 어떤가"라서 스냅샷에 없다. 표시용으로만 붙인다.
        List<Subscription> subscriptions = subscriptionRepository.findAllByUserIncludingDeleted(user);
        Map<Long, Subscription> current = subscriptions.stream()
                .collect(Collectors.toMap(Subscription::getId, Function.identity(), (a, b) -> a));
        PauseHistory pauseHistory = loadPauseHistory(subscriptions);

        Map<Long, List<BillingRecord>> bySubscription = covering.stream()
                .collect(Collectors.groupingBy(BillingRecord::getSubscriptionId));

        List<SubscriptionExpenseDetail> details = Stream.concat(
                        // 한 달에 같은 구독의 결제가 둘 이상일 일은 없지만(주기 최소 1개월), 있어도 합산한다.
                        bySubscription.values().stream()
                                .map(rows -> toDetail(rows, target, current, pauseHistory,
                                        billedBySubscription, scheduledBySubscription)),
                        billedNothing(subscriptions, bySubscription.keySet(), target, pauseHistory))
                .sorted(Comparator.comparingLong(SubscriptionExpenseDetail::appliedMonthlyAmount).reversed())
                .toList();

        return new MonthlyExpenseDetailResponse(target.getYear(), target.getMonthValue(), totalAmount, details);
    }

    /**
     * 그 달에 청구가 하나도 없던 구독을 <b>0원 행</b>으로 만든다.
     *
     * <p>없으면 정지한 구독이 목록에서 통째로 사라져 "등록해둔 구독이 없어졌다"처럼 보인다. 금액은
     * 전부 0이라 합계는 그대로이고, 지출이 없었다는 사실도 그대로 표현된다.
     *
     * <p>그 달에 <b>존재하지 않던</b> 구독은 넣지 않는다 — 등록 전의 달에 유령 행이 뜨기 때문이다.
     * 첫 결제일이 아직 오지 않은 구독도 같은 이유로 뺀다. 삭제된 구독은 이미 청구된 과거만 이력으로
     * 남기고 여기서는 만들지 않는다.
     */
    private Stream<SubscriptionExpenseDetail> billedNothing(List<Subscription> subscriptions,
                                                            Set<Long> alreadyListed, YearMonth target,
                                                            PauseHistory pauseHistory) {
        LocalDate monthEnd = target.atEndOfMonth();
        return subscriptions.stream()
                .filter(s -> !alreadyListed.contains(s.getId()))
                .filter(s -> !s.isDeleted())
                .filter(s -> !YearMonth.from(s.getCreatedAt()).isAfter(target))
                .filter(s -> !s.getFirstBillingDate().isAfter(monthEnd))
                .map(s -> new SubscriptionExpenseDetail(
                        s.getId(),
                        s.getServiceName(),
                        s.getServiceCode(),
                        s.getCategory(),
                        s.getCategory().getDisplayName(),
                        s.getBillingCycle(),
                        0L,
                        0L,
                        0L,
                        0L,
                        s.getFirstBillingDate(),
                        null,       // 그 달에 도래한 청구가 없다
                        s.getStatus(),
                        pauseHistory.statusIn(s, target),
                        false       // 삭제된 구독은 위에서 걸러진다
                ));
    }

    private SubscriptionExpenseDetail toDetail(List<BillingRecord> rows, YearMonth target,
                                               Map<Long, Subscription> current, PauseHistory pauseHistory,
                                               Map<Long, Long> billedBySubscription,
                                               Map<Long, Long> scheduledBySubscription) {
        BillingRecord latest = rows.stream()
                .max(Comparator.comparing(BillingRecord::getBillingDate))
                .orElseThrow();
        Subscription subscription = current.get(latest.getSubscriptionId());

        return new SubscriptionExpenseDetail(
                latest.getSubscriptionId(),
                latest.getServiceName(),
                latest.getServiceCode(),
                latest.getCategory(),
                latest.getCategory().getDisplayName(),
                latest.getBillingCycle(),
                latest.getAmount(),
                burden(rows, target),
                billedBySubscription.getOrDefault(latest.getSubscriptionId(), 0L),
                scheduledBySubscription.getOrDefault(latest.getSubscriptionId(), 0L),
                // 앵커는 구독의 현재 값, 청구일은 스냅샷의 값 — 서로 다른 사실이라 필드를 나눠 싣는다.
                subscription != null ? subscription.getFirstBillingDate() : latest.getBillingDate(),
                latest.getBillingDate(),
                subscription != null ? subscription.getStatus() : null,
                subscription != null ? pauseHistory.statusIn(subscription, target) : null,
                subscription == null || subscription.isDeleted()
        );
    }

    private PauseHistory loadPauseHistory(List<Subscription> subscriptions) {
        List<Long> ids = subscriptions.stream().map(Subscription::getId).toList();
        return new PauseHistory(ids.isEmpty()
                ? Map.of()
                : pausePeriodRepository.findBySubscriptionIdIn(ids).stream()
                        .collect(Collectors.groupingBy(SubscriptionPausePeriod::getSubscriptionId)));
    }

    /**
     * 정지 구간 이력으로 "그 달 상태"를 판정한다. 판정 시점은 <b>그 달의 끝</b>이고, 진행 중인 달이면
     * 오늘이다 — 아직 오지 않은 시점의 상태를 단정하지 않기 위해서다.
     *
     * <p>이력이 없는 구독은 현재 상태가 아니라 {@code ACTIVE} 로 답한다. 현재 정지 중이라는 사실을
     * 과거 달에 투영하면 정상 결제된 달에 "일시정지" 라벨이 붙는다 — 이 표를 만든 이유가 그것이다.
     * 다만 이력이 생기기 전부터 정지 중이던 구독은 마이그레이션이 열린 구간을 만들어 두므로,
     * 그 시작 시각 이후의 달은 정상적으로 PAUSED 로 나온다.
     */
    private record PauseHistory(Map<Long, List<SubscriptionPausePeriod>> periodsBySubscription) {

        SubscriptionStatus statusIn(Subscription subscription, YearMonth target) {
            LocalDateTime monthEnd = target.atEndOfMonth().atTime(LocalTime.MAX);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime instant = now.isBefore(monthEnd) ? now : monthEnd;

            boolean paused = periodsBySubscription.getOrDefault(subscription.getId(), List.of()).stream()
                    .anyMatch(period -> period.covers(instant));
            return paused ? SubscriptionStatus.PAUSED : SubscriptionStatus.ACTIVE;
        }
    }

    /**
     * {@code from}~{@code to} 달의 부담을 계산하는 데 필요한 결제 기록을 읽는다.
     *
     * <p>시작을 {@link #MAX_COVERAGE_MONTHS} 만큼 앞당기는 게 핵심이다 — 연간 결제는 결제월부터
     * 12개월에 걸쳐 부담이 남으므로, 조회 구간의 결제만 읽으면 작년에 낸 연간 구독이 통째로 빠진다.
     */
    private List<BillingRecord> loadRecords(Long userId, YearMonth from, YearMonth to) {
        findUser(userId);   // 없는 사용자는 빈 목록이 아니라 404
        return billingRecordRepository.findByUserIdAndBillingDateBetween(
                userId,
                from.minusMonths(MAX_COVERAGE_MONTHS - 1L).atDay(1),
                to.atEndOfMonth()
        );
    }

    /**
     * <b>진행 중인 달</b>에 한해, 아직 결제일이 오지 않은 활성 구독의 예정 결제를 얹는다.
     *
     * <p>왜 필요한가: 15일에 결제되는 월간 구독은 이번 달 15일이 되기 전까지 결제 기록이 없다. 기록만
     * 보면 "이번 달 지출 0원"이 되는데, 사용자는 구독을 유지 중이고 대시보드는 매달 부담을 보여준다.
     * 이번 달은 아직 끝나지 않았으므로 <b>예정된 결제까지 포함</b>하는 쪽이 화면의 의미와 맞다.
     *
     * <p>지난 달은 절대 손대지 않고 저장된 스냅샷만 쓴다. 미래 달도 아니다. 이미 이번 달 부담이 걸린
     * 구독(월초에 기록됐거나, 작년 연간 기록이 아직 커버 중)은 건너뛰므로 같은 일정이 두 번 잡히지 않고,
     * 청구 예정일이 지나면 예정분이 저장된 기록으로 자연스럽게 대체된다.
     *
     * <p>여기서 만드는 {@link BillingRecord} 는 <b>저장하지 않는 임시 객체</b>다. 예정 결제를 DB에
     * 미리 넣으면 그 뒤 정지·해지·가격변경이 일어났을 때 일어나지 않은 결제가 이력으로 굳는다.
     */
    private List<BillingRecord> withCurrentMonthProjection(User user, List<BillingRecord> records,
                                                           YearMonth from, YearMonth to) {
        YearMonth currentMonth = YearMonth.now();
        if (currentMonth.isBefore(from) || currentMonth.isAfter(to)) {
            return records;
        }

        LocalDate today = LocalDate.now();
        List<BillingRecord> projected = new ArrayList<>(records);
        for (Subscription subscription : subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user)) {
            if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                continue;   // 정지 중이면 이번 달 결제 예정도 없다
            }
            boolean alreadyCovered = records.stream()
                    .anyMatch(r -> r.getSubscriptionId().equals(subscription.getId()) && r.coversMonth(currentMonth));
            if (alreadyCovered) {
                continue;
            }
            LocalDate nextBillingDate = NextBillingDateCalculator.calculate(subscription, today);
            if (YearMonth.from(nextBillingDate).equals(currentMonth)) {
                projected.add(BillingRecord.snapshot(subscription, nextBillingDate));
            }
        }
        return projected;
    }

    /** 해당 월에 부담이 걸리는 기록만. */
    private List<BillingRecord> covering(List<BillingRecord> records, YearMonth month) {
        return records.stream().filter(r -> r.coversMonth(month)).toList();
    }

    /** 해당 월의 월평균 부담 합계. */
    private long burden(List<BillingRecord> records, YearMonth month) {
        return records.stream().mapToLong(r -> r.burdenFor(month)).sum();
    }

    private List<BillingRecord> byCycle(List<BillingRecord> records, BillingCycle cycle) {
        return records.stream().filter(r -> r.getBillingCycle() == cycle).toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
