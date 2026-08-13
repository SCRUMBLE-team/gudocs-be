package com.scrumble.gudocs.subscriptions.repository;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionPausePeriod;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPausePeriodRepository extends JpaRepository<SubscriptionPausePeriod, Long> {

    /** 아직 재개되지 않은 구간. 구독당 최대 1개다(정지 중일 때만 존재). */
    Optional<SubscriptionPausePeriod> findBySubscriptionIdAndEndedAtIsNull(Long subscriptionId);

    /** 청구 기록을 만들기 전에 "그날 정지였나"를 확인하는 용도. */
    List<SubscriptionPausePeriod> findBySubscriptionId(Long subscriptionId);

    /** 지출 상세에서 여러 구독의 "그 달 상태"를 한 번에 판정하려고 통째로 읽는다. */
    List<SubscriptionPausePeriod> findBySubscriptionIdIn(List<Long> subscriptionIds);

    /**
     * 회원 탈퇴 정리용. 구독보다 <b>먼저</b> 지워야 한다 — FK 가 걸려 있어 남으면 구독 삭제가 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SubscriptionPausePeriod p WHERE p.subscriptionId IN "
            + "(SELECT s.id FROM Subscription s WHERE s.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
