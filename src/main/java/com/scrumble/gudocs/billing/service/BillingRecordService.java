package com.scrumble.gudocs.billing.service;

import com.scrumble.gudocs.billing.entity.BillingRecord;
import com.scrumble.gudocs.billing.repository.BillingRecordRepository;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 구독 등록정보 기반 청구 스냅샷을 만드는 쪽. 읽는 쪽은 {@code ExpenseService} 다.
 *
 * <p>트랜잭션을 클래스에 걸지 않는다 — 한 구독의 기록 실패가 다른 구독까지 되돌리면 배치 하나가
 * 그날 전체 지출 기록을 날린다. 행 단위로 커밋하고 실패는 로그로 남긴다(다음 실행이 메운다).
 */
@Service
@RequiredArgsConstructor
public class BillingRecordService {

    private static final Logger log = LoggerFactory.getLogger(BillingRecordService.class);

    /**
     * 일일 배치가 거슬러 올라가 확인하는 일수. 배포·장애로 배치를 며칠 걸러도 그만큼은 저절로 메워진다.
     *
     * <p>짧게 두는 데는 이유가 있다: 이 구간은 "지금 ACTIVE 면 그때도 ACTIVE 였다"고 가정하는데,
     * 멀리 거슬러 갈수록 그 가정이 틀릴 여지가 커진다(그 사이 정지했다 재개했으면 정지 구간까지
     * 결제로 만들어 버린다). 며칠 수준이면 실질적으로 안전하다.
     */
    private static final int LOOKBACK_DAYS = 3;

    /** 신규 등록 시 과거 결제를 만들어 줄 최대 기간. 아주 오래된 앵커로 수백 행이 생기는 것을 막는다. */
    private static final int MAX_BACKFILL_MONTHS = 24;

    private final BillingRecordRepository billingRecordRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 일일 배치 진입점. {@code today} 와 직전 {@link #LOOKBACK_DAYS} 일 사이에 결제일이 있는
     * 활성·미삭제 구독의 결제를 기록한다.
     *
     * <p>정지·삭제된 구독은 조회 대상이 아니라 <b>행이 생기지 않고, 그래서 그 달 지출이 0이 된다.</b>
     * 정지 이력을 따로 관리하지 않는 이유가 이것이다.
     */
    public int recordDueBillings(LocalDate today) {
        LocalDate from = today.minusDays(LOOKBACK_DAYS);
        List<Subscription> targets = subscriptionRepository.findActiveForBillingReminder();

        int created = 0;
        for (Subscription subscription : targets) {
            created += record(subscription, from, today);
        }
        log.info("결제 기록 배치 완료 today={} 대상구독={} 생성={}", today, targets.size(), created);
        return created;
    }

    /**
     * 구독 등록 시 과거 청구 일정을 현재 입력값으로 추정해 채운다. 등록하자마자 지출 화면이 비어
     * 보이지 않게 하는 보조 데이터이며, 과거 카드 승인 내역을 복원한 것이 아니다.
     *
     * <p>미래 날짜를 적었다면 아직 청구 예정일이 도래하지 않았으므로 아무것도 만들지 않는다.
     */
    public int backfillPastBillings(Subscription subscription, LocalDate today) {
        LocalDate earliest = today.minusMonths(MAX_BACKFILL_MONTHS);
        LocalDate from = subscription.getFirstBillingDate().isBefore(earliest)
                ? earliest
                : subscription.getFirstBillingDate();
        return record(subscription, from, today);
    }

    /**
     * {@code [from, to]} 구간의 결제일마다 기록을 남긴다. 이미 있는 날짜는 건너뛴다 — 같은 구간을
     * 몇 번 실행해도 결과가 같다(멱등).
     */
    private int record(Subscription subscription, LocalDate from, LocalDate to) {
        List<LocalDate> dates = billingDatesBetween(subscription, from, to);
        if (dates.isEmpty()) {
            return 0;
        }

        Set<LocalDate> already = new HashSet<>(
                billingRecordRepository.findBillingDates(subscription.getId(), from, to));

        int created = 0;
        for (LocalDate date : dates) {
            if (already.contains(date)) {
                continue;
            }
            try {
                billingRecordRepository.save(BillingRecord.snapshot(subscription, date));
                created++;
            } catch (DataIntegrityViolationException e) {
                // UNIQUE(subscription_id, billing_date) 위반 = 다른 실행이 먼저 기록함. 정상 경로다.
                log.debug("결제 기록 중복 건너뜀 subscriptionId={} date={}", subscription.getId(), date);
            } catch (RuntimeException e) {
                log.error("결제 기록 실패 subscriptionId={} date={}", subscription.getId(), date, e);
            }
        }
        return created;
    }

    /**
     * 구간 안의 결제일 목록. 결제일 계산은 {@link NextBillingDateCalculator} 단일 소스를 재사용한다
     * (월말 클램핑 같은 규칙이 조회·알림·기록에서 갈라지지 않게).
     */
    private List<LocalDate> billingDatesBetween(Subscription subscription, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate billingDate = NextBillingDateCalculator.calculate(subscription, cursor);
            if (billingDate.isAfter(to)) {
                break;
            }
            dates.add(billingDate);
            cursor = billingDate.plusDays(1);
        }
        return dates;
    }
}
