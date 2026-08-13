package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.util.BillingReminderCalculator;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 결제 예정 알림 발송 배치.
 * <p>
 * D-3(결제 3일 전)과 결제일 당일 두 시점에만 발송하며, <b>구독별로 알림 1건</b>을 보낸다.
 * 실제 발송/중복방지/재시도는 공통 {@link NotificationSender}에 위임한다.
 * <p>
 * 예전에는 같은 결제일 구독을 하나로 묶어 보냈다. 묶음을 버린 이유는 <b>알림이 특정 구독을 가리키지
 * 못해 클릭해도 구독 상세로 갈 수 없기 때문</b>이다. 묶임이 실제로 일어나는 경우도 대부분 2건이라
 * (결제일은 구독 시작일에 걸려 한 달에 흩어진다) 묶어서 얻는 알림 수 절감이 크지 않다.
 * dedup 키에 subscription_id 가 포함돼 있어(V7) 구독별 발송도 같은 날 중복 없이 멱등하다.
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
    /** 알림 클릭 시 이동할 구독 상세 경로. 뒤에 구독 id 가 붙는다. */
    private static final String SUBSCRIPTION_DETAIL_PATH = "/subscriptions/";

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationSender notificationSender;

    @Value("${app.firebase.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * 오늘 기준 D-3·당일 결제 예정 구독을 찾아 아직 보내지 않은 대상에게 구독별로 푸시를 발송한다.
     * 절약 후보로 체크한 구독의 D-3은 결제 예정 대신 해지 알림으로 대체한다.
     */
    public void dispatchDueReminders(LocalDate today) {
        List<Subscription> active = subscriptionRepository.findActiveForBillingReminder();
        List<DueBilling> dueList = BillingReminderCalculator.findDue(active, today, REMINDER_OFFSETS);

        for (DueBilling due : dueList) {
            NotificationDraft draft = isCancelReminderTarget(due) ? toCancelDraft(due) : toBillingDraft(due);
            notificationSender.send(due.subscription().getUser().getId(), draft);
        }
    }

    /** 절약 후보로 체크해 둔 구독의 D-3 — 결제 예정 대신 해지 알림을 보낼 대상. */
    private boolean isCancelReminderTarget(DueBilling due) {
        return due.daysUntil() == CANCEL_REMINDER_OFFSET && due.subscription().isSavingsSelected();
    }

    /**
     * 시연·검증용 미리보기({@code NotificationPreviewService})가 <b>실제와 같은 문구</b>를 쓰도록
     * package-private 으로 연다. 미리보기가 문구를 따로 만들면 시연에서 보여주는 것이 진짜 알림이
     * 아니게 되고, 문구를 고칠 때 두 곳이 갈라진다.
     */
    NotificationDraft toCancelDraft(DueBilling due) {
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

    /** 미리보기와 공유한다 — 위 {@link #toCancelDraft} 주석 참고. */
    NotificationDraft toBillingDraft(DueBilling due) {
        Subscription subscription = due.subscription();
        Map<String, String> data = Map.of(
                "type", NotificationType.BILLING_REMINDER.name(),
                "subscriptionId", String.valueOf(subscription.getId()),
                "link", frontendBaseUrl + SUBSCRIPTION_DETAIL_PATH + subscription.getId()
        );
        String when = due.daysUntil() == 0 ? "오늘" : due.daysUntil() + "일 후";
        return new NotificationDraft(
                NotificationType.BILLING_REMINDER,
                due.targetDate(),
                due.daysUntil(),
                subscription.getId(),
                subscription.getServiceName() + " 결제 예정",
                String.format(Locale.KOREA, "%s %,d원이 결제될 예정이에요.", when, subscription.getPrice()),
                data);
    }
}
