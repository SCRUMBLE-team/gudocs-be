package com.scrumble.gudocs.subscriptions.repository;

import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
