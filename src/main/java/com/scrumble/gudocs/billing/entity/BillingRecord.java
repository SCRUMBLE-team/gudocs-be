package com.scrumble.gudocs.billing.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 구독 등록정보를 기준으로 결제 예정일이 도래했을 때 남기는 청구 스냅샷.
 * <b>지출 분석의 단일 소스</b>다.
 *
 * <p><b>왜 스냅샷인가</b>: 과거 지출을 구독 테이블에서 매번 계산하면, 사용자가 나중에 가격·카테고리를
 * 바꾸거나 정지·삭제하는 순간 <i>과거가 따라 움직인다.</i> 필드마다 변경 이력 테이블을 두는 방법도 있지만
 * (가격 이력, 카테고리 이력, 상태 이력 …) 가변 필드가 늘 때마다 이력이 하나씩 늘고 조회는 시점 조인이
 * 된다. 결제 시점에 그때 값을 통째로 얼려 한 줄 남기면 그 전부가 필요 없어진다.
 *
 * <p>특히 <b>일시정지는 행의 부재로 표현된다</b> — 정지 중인 달은 배치가 도는 시점에 ACTIVE 가 아니라
 * 행이 생기지 않고, 그래서 그 달 지출은 0이다. 정지/재개를 몇 번 반복해도 저절로 맞는다.
 *
 * <p><b>카드·은행의 실제 결제 증빙이 아니다.</b> {@code billingDate}는 구독 앵커와 주기로 계산하고,
 * {@code amount}는 그 시점의 {@code Subscription.price}를 복사한다. 따라서 이 행만으로 실제 승인 여부나
 * 실제 청구액을 판정해서는 안 된다. 특히 등록 시 과거 백필 행은 현재 입력값으로 과거 일정을 추정한 값이다.
 *
 * <p>행은 만들고 나면 수정하지 않는다. 구독 가격을 고쳐도 이미 기록한 당시의 등록 금액은 그대로다.
 * 이 성질이 깨지면 현재 값 변경으로부터 과거 분석을 격리하려는 목적이 사라진다.
 */
@Entity
@Table(
        name = "billing_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_billing_records_subscription_date",
                columnNames = {"subscription_id", "billing_date"}
        ),
        indexes = @Index(name = "idx_billing_records_user_date", columnList = "user_id, billing_date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BillingRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연관관계가 아닌 값 컬럼. 지출 조회가 구독 조인 없이 사용자+기간만으로 끝나게 한다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 어떤 구독의 결제였는지. 상세 화면의 링크·표시용이고, 금액 계산은 이 행의 스냅샷만으로 끝난다.
     * 구독이 soft delete 돼도 이 행은 남는다.
     */
    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "billing_date", nullable = false)
    private LocalDate billingDate;

    /** 기록 생성 시점의 {@code Subscription.price}. 외부 결제수단에서 확인한 승인 금액이 아니다. */
    @Column(nullable = false)
    private Long amount;

    // --- 아래는 결제 시점의 구독 정보 스냅샷. 지금 구독이 어떤 상태든 과거 표시는 이 값으로 한다. ---

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_code", length = 64)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    public static BillingRecord snapshot(Subscription subscription, LocalDate billingDate) {
        return BillingRecord.builder()
                .userId(subscription.getUser().getId())
                .subscriptionId(subscription.getId())
                .billingDate(billingDate)
                .amount(subscription.getPrice())
                .serviceName(subscription.getServiceName())
                .serviceCode(subscription.getServiceCode())
                .category(subscription.getCategory())
                .billingCycle(subscription.getBillingCycle())
                .build();
    }

    /**
     * 이 결제가 {@code month} 에 부담시키는 금액(월평균 부담).
     *
     * <p>결제월부터 {@code coverageMonths} 개월에 걸쳐 균등 분산한다 — 연간 120,000원을 3월에 냈다면
     * 3월~다음해 2월에 10,000원씩. 커버 범위 밖의 달은 0.
     *
     * <p>나눗셈은 기존 지출 분석과 같이 소수를 버린다(Long). 이 값은 "체감 부담"을 보여주는 지표다.
     * {@link #getAmount()} 합계 역시 등록정보 기준 청구액이지 실제 현금흐름 증빙은 아니다.
     */
    public long burdenFor(YearMonth month) {
        return coversMonth(month) ? amount / billingCycle.getCoverageMonths() : 0L;
    }

    public boolean coversMonth(YearMonth month) {
        YearMonth start = YearMonth.from(billingDate);
        YearMonth end = start.plusMonths(billingCycle.getCoverageMonths() - 1L);
        return !month.isBefore(start) && !month.isAfter(end);
    }

    public boolean billedIn(YearMonth month) {
        return YearMonth.from(billingDate).equals(month);
    }
}
