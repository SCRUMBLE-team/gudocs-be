package com.scrumble.gudocs.notification.repository;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    Optional<UserNotification> findByUserIdAndTypeAndTargetDateAndRemindOffset(
            Long userId, NotificationType type, LocalDate targetDate, int remindOffset);

    void deleteAllByUserId(Long userId);
}
