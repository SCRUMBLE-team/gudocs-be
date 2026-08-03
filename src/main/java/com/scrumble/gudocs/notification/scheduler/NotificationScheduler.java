package com.scrumble.gudocs.notification.scheduler;

import com.scrumble.gudocs.notification.service.NotificationDispatchService;
import com.scrumble.gudocs.notification.service.SubscriptionReviewDispatchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 결제 예정 알림 스케줄러. cron은 app.firebase.notification-cron(env FCM_NOTIFICATION_CRON)으로 관리한다.
 * Firebase 활성 환경에서만 등록된다(비활성이면 발송 대상이 없으므로 스케줄 자체를 두지 않는다).
 */
@Component
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationDispatchService dispatchService;
    private final SubscriptionReviewDispatchService reviewDispatchService;

    @Scheduled(cron = "${app.firebase.notification-cron}", zone = "Asia/Seoul")
    public void dispatchBillingReminders() {
        LocalDate today = LocalDate.now(ZONE);
        log.info("결제 예정 알림 스케줄러 시작 today={}", today);
        dispatchService.dispatchDueReminders(today);
        log.info("결제 예정 알림 스케줄러 종료 today={}", today);
    }

    @Scheduled(cron = "${app.firebase.review-cron}", zone = "Asia/Seoul")
    public void dispatchSubscriptionReviews() {
        LocalDate today = LocalDate.now(ZONE);
        log.info("구독 검사 유도 알림 스케줄러 시작 today={}", today);
        reviewDispatchService.dispatchDueReviews(today);
        log.info("구독 검사 유도 알림 스케줄러 종료 today={}", today);
    }
}
