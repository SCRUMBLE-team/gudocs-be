package com.scrumble.gudocs.notification.repository;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    // dedup 키 전체로 조회한다 — 해지 알림은 구독별로 발송되므로 subscription_id 까지 봐야 같은 날
    // 서로 다른 구독의 이력을 구분할 수 있다.
    Optional<UserNotification> findByUserIdAndTypeAndTargetDateAndRemindOffsetAndSubscriptionId(
            Long userId, NotificationType type, LocalDate targetDate, int remindOffset, long subscriptionId);

    void deleteAllByUserId(Long userId);
}
