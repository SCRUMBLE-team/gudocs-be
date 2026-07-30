package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushResult;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import com.scrumble.gudocs.notification.util.BillingReminderCalculator;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 결제 예정 알림 발송 배치.
 * <p>
 * 트랜잭션 경계: 클래스/메서드 레벨 @Transactional을 두지 않는다. 각 repository 저장이
 * 개별 트랜잭션으로 커밋되므로, 특정 기기 발송 실패(캐치됨)가 이미 기록한 알림 이력이나
 * 다른 기기 처리를 롤백하지 않는다. 중복 발송은 UserNotification의 UNIQUE 제약으로 막는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final int WINDOW_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;
    private final PushRegistrationRepository pushRegistrationRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final PushSender pushSender;

    @Value("${app.firebase.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * 오늘 기준 7일 이내 결제 예정 구독을 찾아 아직 보내지 않은 대상에게 푸시를 발송한다.
     */
    public void dispatchDueReminders(LocalDate today) {
        List<Subscription> active = subscriptionRepository.findActiveForBillingReminder();
        List<DueBilling> dueList = BillingReminderCalculator.findDue(active, today, WINDOW_DAYS);

        for (DueBilling due : dueList) {
            recordIfAbsent(due).ifPresent(notification -> sendToUserDevices(due, notification));
        }
    }

    /**
     * 중복 방지: 이미 같은 (user, subscription, type, targetDate) 이력이 있으면 건너뛴다.
     * 다중 서버 경합 시에도 UNIQUE 제약 위반을 캐치해 한 번만 기록되도록 한다.
     */
    private Optional<UserNotification> recordIfAbsent(DueBilling due) {
        Long userId = due.subscription().getUser().getId();
        Long subscriptionId = due.subscription().getId();

        if (userNotificationRepository.existsByUserIdAndSubscriptionIdAndTypeAndTargetDate(
                userId, subscriptionId, NotificationType.BILLING_REMINDER, due.targetDate())) {
            return Optional.empty();
        }

        UserNotification notification = UserNotification.builder()
                .userId(userId)
                .subscriptionId(subscriptionId)
                .type(NotificationType.BILLING_REMINDER)
                .title(buildTitle(due))
                .body(buildBody(due))
                .targetDate(due.targetDate())
                .build();
        try {
            return Optional.of(userNotificationRepository.saveAndFlush(notification));
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 먼저 기록함 → 이번 실행에서는 발송하지 않음
            return Optional.empty();
        }
    }

    /**
     * 사용자의 활성 기기에 각각 발송한다. 한 기기 실패가 다른 기기 발송을 막지 않는다.
     */
    private void sendToUserDevices(DueBilling due, UserNotification notification) {
        Long userId = due.subscription().getUser().getId();
        List<PushRegistration> registrations = pushRegistrationRepository.findByUserIdAndEnabledTrue(userId);
        PushMessage message = buildMessage(due, notification);

        boolean anySuccess = false;
        for (PushRegistration registration : registrations) {
            try {
                PushResult result = pushSender.send(registration.getFid(), message);
                if (result == PushResult.SUCCESS) {
                    anySuccess = true;
                } else if (result == PushResult.INVALID_TOKEN) {
                    registration.disable();
                    pushRegistrationRepository.save(registration);
                }
            } catch (RuntimeException e) {
                // 개별 기기 예외는 삼키고 다음 기기 계속 처리
                log.warn("푸시 발송 중 예외 registrationId={}", registration.getId(), e);
            }
        }

        if (anySuccess) {
            notification.markSent(LocalDateTime.now());
            userNotificationRepository.save(notification);
        }
    }

    private PushMessage buildMessage(DueBilling due, UserNotification notification) {
        Long subscriptionId = due.subscription().getId();
        Map<String, String> data = Map.of(
                "type", NotificationType.BILLING_REMINDER.name(),
                "subscriptionId", String.valueOf(subscriptionId),
                "link", frontendBaseUrl + "/subscriptions/" + subscriptionId
        );
        return new PushMessage(notification.getTitle(), notification.getBody(), data);
    }

    private String buildTitle(DueBilling due) {
        return due.subscription().getServiceName() + " 결제 예정";
    }

    private String buildBody(DueBilling due) {
        return String.format("%d일 후 %,d원이 결제될 예정이에요.",
                due.daysUntil(), due.subscription().getPrice());
    }
}
