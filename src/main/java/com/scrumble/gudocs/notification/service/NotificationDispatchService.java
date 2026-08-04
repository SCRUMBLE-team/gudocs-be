package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.util.BillingReminderCalculator;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 결제 예정 알림 발송 배치.
 * <p>
 * D-3(결제 3일 전)과 결제일 당일 두 시점에만 발송하며, <b>같은 유저의 같은 결제일 구독은 하나로 묶어</b>
 * 알림 1건으로 보낸다(같은 결제일 = 같은 발송 단계이므로 유저+결제일 단위로 묶인다).
 * 실제 발송/중복방지/재시도는 공통 {@link NotificationSender}에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    /** 결제 며칠 전에 알릴지: 3일 전(D-3)과 당일(D-0). */
    private static final Set<Integer> REMINDER_OFFSETS = Set.of(3, 0);
    /** 알림 클릭 시 이동할 프론트 알림함 경로. */
    private static final String NOTIFICATIONS_PATH = "/notifications";

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationSender notificationSender;

    @Value("${app.firebase.frontend-base-url}")
    private String frontendBaseUrl;

    /** 유저+결제일(=발송 단계) 단위 묶음 알림 대상. */
    private record BillingGroup(Long userId, LocalDate targetDate, int daysUntil, List<Subscription> subscriptions) {
    }

    /** 묶음 그룹 키: 같은 유저의 같은 결제일. */
    private record GroupKey(Long userId, LocalDate targetDate) {
    }

    /**
     * 오늘 기준 D-3·당일 결제 예정 구독을 유저·결제일 단위로 묶어, 아직 보내지 않은 대상에게 푸시를 발송한다.
     */
    public void dispatchDueReminders(LocalDate today) {
        List<Subscription> active = subscriptionRepository.findActiveForBillingReminder();
        List<DueBilling> dueList = BillingReminderCalculator.findDue(active, today, REMINDER_OFFSETS);

        for (BillingGroup group : groupByUserAndBillingDate(dueList)) {
            notificationSender.send(group.userId(), toDraft(group));
        }
    }

    /**
     * 같은 유저의 같은 결제일 구독을 하나의 묶음으로 만든다. dueList가 결제일 오름차순이라 그룹 순서도 유지된다.
     */
    private List<BillingGroup> groupByUserAndBillingDate(List<DueBilling> dueList) {
        // key: (userId, targetDate) — 같은 결제일이면 daysUntil도 동일
        Map<GroupKey, List<DueBilling>> grouped = dueList.stream()
                .collect(Collectors.groupingBy(
                        d -> new GroupKey(d.subscription().getUser().getId(), d.targetDate()),
                        LinkedHashMap::new, Collectors.toList()));

        return grouped.values().stream()
                .map(members -> new BillingGroup(
                        members.get(0).subscription().getUser().getId(),
                        members.get(0).targetDate(),
                        members.get(0).daysUntil(),
                        members.stream().map(DueBilling::subscription).toList()))
                .toList();
    }

    private NotificationDraft toDraft(BillingGroup group) {
        Map<String, String> data = Map.of(
                "type", NotificationType.BILLING_REMINDER.name(),
                "link", frontendBaseUrl + NOTIFICATIONS_PATH
        );
        return new NotificationDraft(
                NotificationType.BILLING_REMINDER,
                group.targetDate(),
                group.daysUntil(),
                buildTitle(group),
                buildBody(group),
                data);
    }

    private String buildTitle(BillingGroup group) {
        String firstName = group.subscriptions().get(0).getServiceName();
        int count = group.subscriptions().size();
        if (count == 1) {
            return firstName + " 결제 예정";
        }
        return firstName + " 외 " + (count - 1) + "건 결제 예정";
    }

    private String buildBody(BillingGroup group) {
        String when = group.daysUntil() == 0 ? "오늘" : group.daysUntil() + "일 후";
        long total = group.subscriptions().stream().mapToLong(Subscription::getPrice).sum();
        int count = group.subscriptions().size();
        if (count == 1) {
            return String.format(Locale.KOREA, "%s %,d원이 결제될 예정이에요.", when, total);
        }
        return String.format(Locale.KOREA, "%s %d건 %,d원이 결제될 예정이에요.", when, count, total);
    }
}
