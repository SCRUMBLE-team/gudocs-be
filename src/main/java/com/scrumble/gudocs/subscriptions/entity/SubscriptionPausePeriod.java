package com.scrumble.gudocs.subscriptions.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 구독이 정지돼 있던 한 구간. 정지할 때 열고({@code ended_at = null}) 재개할 때 닫는다.
 *
 * <p><b>왜 따로 두는가</b>: {@code Subscription.pausedAt} 은 "마지막으로 정지한 시각" 하나뿐이라
 * 재개하면 지워진다. 그 값으로 과거 달을 판단하면 6월 정지 → 7월 재개 → 8월 재정지 같은 경우에
 * "6월은 정상이었다"고 <b>틀린 답</b>을 하게 된다. 되돌릴 수 있는 사건은 현재 값 컬럼이 아니라
 * 사건 기록으로 남겨야 한다(AGENTS.md).
 *
 * <p><b>금액에는 절대 쓰지 않는다.</b> 지출은 {@code billing_records} 가 단일 소스이고, 이 표는
 * "그 달에 정지 중이었다"는 <b>표시용 라벨</b>에만 쓴다. 상태로 금액을 되계산하면 이력과 기록이
 * 어긋날 때 있지도 않았던 결제를 만들어낸다.
 *
 * <p>{@code subscriptionId} 는 연관관계가 아니라 값 컬럼이다({@code BillingRecord} 와 같은 이유 —
 * 조회가 구독 조인 없이 끝난다). 무결성은 DB FK 로 잡고, 회원 탈퇴 시 구독보다 먼저 정리한다.
 */
@Entity
@Table(name = "subscription_pause_periods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPausePeriod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 재개 시각. null 이면 아직 정지 중인 열린 구간이다. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    private SubscriptionPausePeriod(Long subscriptionId, LocalDateTime startedAt) {
        this.subscriptionId = subscriptionId;
        this.startedAt = startedAt;
    }

    public static SubscriptionPausePeriod open(Long subscriptionId, LocalDateTime startedAt) {
        return new SubscriptionPausePeriod(subscriptionId, startedAt);
    }

    /** 재개 처리. 이미 닫힌 구간은 건드리지 않는다(재개 요청이 두 번 와도 첫 시각을 지킨다). */
    public void close(LocalDateTime endedAt) {
        if (this.endedAt == null) {
            this.endedAt = endedAt;
        }
    }

    /** 주어진 시점에 이 구간이 정지 상태였는지. 시작은 포함, 종료는 제외한다. */
    public boolean covers(LocalDateTime instant) {
        if (startedAt.isAfter(instant)) {
            return false;
        }
        return endedAt == null || endedAt.isAfter(instant);
    }

    /**
     * 그 <b>날짜</b>에 정지 구간이 조금이라도 걸쳤는지. 청구 기록을 만들지 말지 판단하는 데 쓴다.
     *
     * <p>하루 안에서 정지·재개가 갈리면 보수적으로 "정지였다"고 본다 — 일어나지 않은 결제를
     * 기록으로 만드는 쪽이 그 반대보다 나쁘다(지출이 실제보다 커지고, 스냅샷은 고치지 않는다).
     */
    public boolean coversDate(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime nextDayStart = date.plusDays(1).atStartOfDay();
        if (!startedAt.isBefore(nextDayStart)) {
            return false;
        }
        return endedAt == null || endedAt.isAfter(dayStart);
    }
}
