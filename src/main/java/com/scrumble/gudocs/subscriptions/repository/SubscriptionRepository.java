package com.scrumble.gudocs.subscriptions.repository;

import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.users.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("SELECT s FROM Subscription s WHERE s.user = :user AND s.deletedAt IS NULL " +
            "ORDER BY s.createdAt DESC")
    List<Subscription> findAllByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT s FROM Subscription s WHERE s.user = :user ORDER BY s.createdAt DESC")
    List<Subscription> findAllByUserIncludingDeleted(@Param("user") User user);

    // 결제 예정 알림 배치용: 삭제되지 않은 ACTIVE 구독. user를 함께 로딩해 배치에서 지연 로딩 없이 사용.
    // (다음 결제일은 앵커+주기로 계산되어 SQL로 표현 불가하므로 여기서는 상태/삭제 조건만 필터링)
    @Query("SELECT s FROM Subscription s JOIN FETCH s.user " +
            "WHERE s.deletedAt IS NULL AND s.status = com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus.ACTIVE")
    List<Subscription> findActiveForBillingReminder();

    // 검사 유도 배치용: 지정한 유저들의 삭제되지 않은 ACTIVE 구독만 조회(대상=활성 기기 보유 유저로 한정해 불필요한 적재 방지).
    @Query("SELECT s FROM Subscription s JOIN FETCH s.user " +
            "WHERE s.deletedAt IS NULL AND s.status = com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus.ACTIVE " +
            "AND s.user.id IN :userIds")
    List<Subscription> findActiveByUserIds(@Param("userIds") List<Long> userIds);

    // 절약하기 화면에서 해지 후보로 체크한 구독. 선택 시각 최신순이며, 한 번의 요청으로 여러 건을
    // 선택하면 시각이 사실상 같으므로 id 로 순서를 확정한다(같은 목록이 매번 같은 순서로 내려가게).
    @Query("SELECT s FROM Subscription s WHERE s.user = :user AND s.deletedAt IS NULL " +
            "AND s.savingsSelectedAt IS NOT NULL ORDER BY s.savingsSelectedAt DESC, s.id DESC")
    List<Subscription> findSavingsSelectedByUser(@Param("user") User user);

    /**
     * 절약 후보 선택을 통째로 대체할 때 쓰는 조회. 사용자의 구독 행을 쓰기 잠금으로 읽어
     * 같은 사용자의 동시 요청을 직렬화한다.
     *
     * <p>잠금이 없으면 두 기기가 동시에 PUT 했을 때 둘 다 "선택 없음" 상태를 읽고 각자 자기 것만
     * 갱신해, A의 요청도 B의 요청도 아닌 합집합이 남는다(대체 API인데 대체가 안 된다).
     * 행 단위로 서로 다른 구독을 건드려 충돌 없이 둘 다 커밋되기 때문이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.user = :user AND s.deletedAt IS NULL")
    List<Subscription> findAllByUserForUpdate(@Param("user") User user);

    boolean existsByUserAndServiceNameIgnoreCaseAndDeletedAtIsNull(User user, String serviceName);

    /**
     * 카탈로그 서비스의 중복 판정. 표시 이름은 바뀔 수 있으므로 code 가 있으면 code 로 본다
     * (같은 넷플릭스를 이름만 다르게 적어 두 번 등록하는 것을 잡아낸다).
     */
    boolean existsByUserAndServiceCodeAndDeletedAtIsNull(User user, String serviceCode);

    @Modifying
    @Transactional
    @Query("DELETE FROM Subscription s WHERE s.user = :user")
    int hardDeleteAllByUser(@Param("user") User user);
}
