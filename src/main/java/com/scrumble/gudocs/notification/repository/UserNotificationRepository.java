package com.scrumble.gudocs.notification.repository;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    boolean existsByUserIdAndSubscriptionIdAndTypeAndTargetDate(
            Long userId, Long subscriptionId, NotificationType type, LocalDate targetDate);

    void deleteAllByUserId(Long userId);
}
