package com.scrumble.gudocs.notification.util;

import com.scrumble.gudocs.notification.service.DueBilling;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 구독 목록에서 지정한 기간(window) 이내에 결제 예정인 대상만 골라낸다.
 * 결제일 계산은 {@link NextBillingDateCalculator} 단일 소스를 재사용한다.
 * (조회/알림/대시보드가 공유하던 계산 로직을 스케줄러·발송 서비스에서도 재사용하도록 추출)
 */
public final class BillingReminderCalculator {

    private BillingReminderCalculator() {
    }

    /**
     * @param subscriptions 대상 구독 (호출자가 ACTIVE·미삭제로 필터링해서 전달)
     * @param today         기준일
     * @param windowDays    오늘 포함 며칠 이내를 대상으로 볼지 (7이면 오늘~오늘+7)
     * @return 결제일 오름차순 DueBilling 목록
     */
    public static List<DueBilling> findDue(List<Subscription> subscriptions, LocalDate today, int windowDays) {
        LocalDate threshold = today.plusDays(windowDays);

        return subscriptions.stream()
                .map(s -> {
                    LocalDate nextBillingDate = NextBillingDateCalculator.calculate(s, today);
                    int daysUntil = (int) ChronoUnit.DAYS.between(today, nextBillingDate);
                    return new DueBilling(s, nextBillingDate, daysUntil);
                })
                .filter(d -> !d.targetDate().isBefore(today) && !d.targetDate().isAfter(threshold))
                .sorted(Comparator.comparing(DueBilling::targetDate))
                .toList();
    }
}
