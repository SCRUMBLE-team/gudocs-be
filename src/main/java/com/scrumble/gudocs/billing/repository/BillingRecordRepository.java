package com.scrumble.gudocs.billing.repository;

import com.scrumble.gudocs.billing.entity.BillingRecord;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {

    /**
     * 지출 분석용 조회. 사용자 + 결제일 구간만으로 끝난다(구독 조인 없음).
     *
     * <p>호출자는 <b>보고 싶은 달보다 넉넉히 앞선 구간</b>을 넘겨야 한다 — 연간 결제는 결제월부터
     * 12개월에 걸쳐 부담이 분산되므로, 3월의 부담을 알려면 작년 4월 결제까지 봐야 한다.
     */
    @Query("SELECT r FROM BillingRecord r WHERE r.userId = :userId " +
            "AND r.billingDate BETWEEN :from AND :to ORDER BY r.billingDate ASC, r.id ASC")
    List<BillingRecord> findByUserIdAndBillingDateBetween(@Param("userId") Long userId,
                                                          @Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);

    /** 이미 기록된 결제일. 배치가 같은 날을 다시 만들지 않도록 미리 걸러내는 용도(최종 방어는 UNIQUE). */
    @Query("SELECT r.billingDate FROM BillingRecord r WHERE r.subscriptionId = :subscriptionId " +
            "AND r.billingDate BETWEEN :from AND :to")
    List<LocalDate> findBillingDates(@Param("subscriptionId") Long subscriptionId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * 회원 탈퇴 정리용. {@code user_id} 가 값 컬럼이라 FK cascade 가 걸리지 않으므로 명시적으로 지운다.
     */
    @Modifying
    @Query("DELETE FROM BillingRecord r WHERE r.userId = :#{#user.id}")
    int deleteAllByUser(@Param("user") User user);
}
