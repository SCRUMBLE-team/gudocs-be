package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushResult;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 알림 1건을 유저의 활성 기기에 발송하고 이력을 남기는 공통 발송기.
 * 결제 알림·검사 유도 등 여러 배치가 공유한다(발송/중복방지/재시도 로직 단일 소스).
 * <p>
 * 트랜잭션 경계: 메서드에 @Transactional을 두지 않는다. 각 repository 저장이 개별 트랜잭션으로
 * 커밋되므로, 특정 기기 발송 실패(캐치됨)가 이미 기록한 알림 이력이나 다른 기기 처리를 롤백하지 않는다.
 * 중복 발송은 UserNotification의 UNIQUE(user_id, type, target_date, remind_offset) 제약으로 막는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final UserNotificationRepository userNotificationRepository;
    private final PushRegistrationRepository pushRegistrationRepository;
    private final PushSender pushSender;

    /**
     * 아직 발송하지 않은 알림이면 유저의 활성 기기에 발송한다. 이미 발송 성공한 이력이면 아무것도 하지 않는다.
     */
    public void send(Long userId, NotificationDraft draft) {
        resolvePending(userId, draft).ifPresent(notification -> deliver(userId, notification, draft));
    }

    /**
     * 발송이 필요한 알림 이력을 준비한다.
     * <ul>
     *   <li>이미 발송 성공(sentAt != null)이면 비어있는 Optional → 건너뜀.</li>
     *   <li>이력은 있으나 미발송(sentAt == null)이면 그 행을 재사용해 재발송.</li>
     *   <li>이력이 없으면 새로 기록. 다중 서버 경합 시 UNIQUE 위반을 캐치해 한 번만 기록.</li>
     * </ul>
     */
    private Optional<UserNotification> resolvePending(Long userId, NotificationDraft draft) {
        Optional<UserNotification> existing = find(userId, draft);
        if (existing.isPresent()) {
            return existing.filter(n -> n.getSentAt() == null);
        }

        UserNotification notification = UserNotification.builder()
                .userId(userId)
                .subscriptionId(null) // 유저 단위/묶음 알림이라 특정 구독에 종속되지 않음
                .type(draft.type())
                .remindOffset(draft.remindOffset())
                .title(draft.title())
                .body(draft.body())
                .targetDate(draft.targetDate())
                .build();
        try {
            return Optional.of(userNotificationRepository.saveAndFlush(notification));
        } catch (DataIntegrityViolationException e) {
            return find(userId, draft).filter(n -> n.getSentAt() == null);
        }
    }

    private Optional<UserNotification> find(Long userId, NotificationDraft draft) {
        return userNotificationRepository.findByUserIdAndTypeAndTargetDateAndRemindOffset(
                userId, draft.type(), draft.targetDate(), draft.remindOffset());
    }

    /**
     * 사용자의 활성 기기에 각각 발송한다. 한 기기 실패가 다른 기기 발송을 막지 않는다.
     */
    private void deliver(Long userId, UserNotification notification, NotificationDraft draft) {
        List<PushRegistration> registrations = pushRegistrationRepository.findByUserIdAndEnabledTrue(userId);
        PushMessage message = new PushMessage(notification.getTitle(), notification.getBody(), draft.pushData());

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
}
