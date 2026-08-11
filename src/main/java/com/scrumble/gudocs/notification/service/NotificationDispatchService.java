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
 * <p>
 * <b>절약 후보로 체크한 구독의 D-3은 결제 예정이 아니라 해지 알림으로 대체한다.</b> 사용자가 이미
 * "해지하겠다"고 표시한 구독에 결제 예정만 알리는 것은 아무 행동으로 이어지지 않기 때문이다. 해지 알림은
 * 클릭 시 해당 구독 상세로 보내야 하므로 묶지 않고 <b>구독별 1건</b>으로 발송한다. 당일(D-0)은 대체하지
 * 않는다 — 그날은 이미 빠져나가는 돈이라 해지 권유보다 결제 사실 통지가 맞다.
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    /** 결제 며칠 전에 알릴지: 3일 전(D-3)과 당일(D-0). */
    private static final Set<Integer> REMINDER_OFFSETS = Set.of(3, 0);
    /** 해지 알림으로 대체하는 발송 단계. 당일은 대체하지 않는다. */
    private static final int CANCEL_REMINDER_OFFSET = 3;
    /** 알림 클릭 시 이동할 프론트 알림함 경로. */
    private static final String NOTIFICATIONS_PATH = "/notifications";
    /** 해지 알림 클릭 시 이동할 구독 상세 경로. 뒤에 구독 id 가 붙는다. */
    private static final String SUBSCRIPTION_DETAIL_PATH = "/subscriptions/";

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
     * 오늘 기준 D-3·당일 결제 예정 구독을 찾아 아직 보내지 않은 대상에게 푸시를 발송한다.
     * 절약 후보로 체크한 구독의 D-3은 해지 알림으로 대체하고(구독별 1건), 나머지는 기존대로
     * 유저·결제일 단위로 묶어 결제 예정 알림 1건을 보낸다.
     */
    public void dispatchDueReminders(LocalDate today) {
        List<Subscription> active = subscriptionRepository.findActiveForBillingReminder();
        List<DueBilling> dueList = BillingReminderCalculator.findDue(active, today, REMINDER_OFFSETS);

        Map<Boolean, List<DueBilling>> split = dueList.stream()
                .collect(Collectors.partitioningBy(this::isCancelReminderTarget));

        for (DueBilling due : split.get(true)) {
            notificationSender.send(due.subscription().getUser().getId(), toCancelDraft(due));
        }
        for (BillingGroup group : groupByUserAndBillingDate(split.get(false))) {
            notificationSender.send(group.userId(), toDraft(group));
        }
    }

    /** 절약 후보로 체크해 둔 구독의 D-3 — 결제 예정 대신 해지 알림을 보낼 대상. */
    private boolean isCancelReminderTarget(DueBilling due) {
        return due.daysUntil() == CANCEL_REMINDER_OFFSET && due.subscription().isSavingsSelected();
    }

    private NotificationDraft toCancelDraft(DueBilling due) {
        Subscription subscription = due.subscription();
        Map<String, String> data = Map.of(
                "type", NotificationType.CANCEL_REMINDER.name(),
                // 구독별 발송이라 단일 id 를 실을 수 있다. 클릭하면 그 구독 상세로 바로 이동한다.
                "subscriptionId", String.valueOf(subscription.getId()),
                "link", frontendBaseUrl + SUBSCRIPTION_DETAIL_PATH + subscription.getId()
        );
        return new NotificationDraft(
                NotificationType.CANCEL_REMINDER,
                due.targetDate(),
                due.daysUntil(),
                subscription.getId(),
                subscription.getServiceName() + ", 계속 이용하시나요?",
                String.format(Locale.KOREA, "해지 후보로 저장한 구독이에요. %d일 후 %,d원이 결제될 예정이에요.",
                        due.daysUntil(), subscription.getPrice()),
                data);
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
        return NotificationDraft.forUser(
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
