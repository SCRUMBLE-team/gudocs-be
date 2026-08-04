package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 구독 검사 유도 알림 발송 배치.
 * <p>
 * 회원 가입일(User.createdAt)을 기준으로 정기 발송한다. 매일 스케줄러가 돌면서 그날의 <b>현재 구독 상태</b>로
 * 주기를 판정하므로, 중복이 해소되면 다음 발송부터 자동으로 4주 주기로 전환된다.
 * <ul>
 *   <li>같은 카테고리 중복 구독 있음 → 가입일로부터 2주(14일)마다 발송</li>
 *   <li>중복 없음 → 가입일로부터 4주(28일)마다 발송</li>
 * </ul>
 * 대상은 활성 기기를 가진(푸시 도달 가능한) 유저로 한정한다. 실제 발송/중복방지는 {@link NotificationSender}에 위임.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionReviewDispatchService {

    /** 카테고리 중복이 있을 때의 발송 주기(일). */
    private static final int PERIOD_WITH_DUPLICATE_DAYS = 14;
    /** 중복이 없을 때의 발송 주기(일). */
    private static final int PERIOD_DEFAULT_DAYS = 28;
    /** 검사 유도 알림 클릭 시 이동할 프론트 구독 점검 경로. */
    private static final String REVIEW_PATH = "/subscriptions";

    private final PushRegistrationRepository pushRegistrationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;

    @Value("${app.firebase.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * 활성 기기를 가진 각 유저에 대해, 가입 경과일과 현재 중복 여부로 오늘이 발송일인지 판정해 검사 유도 알림을 보낸다.
     */
    public void dispatchDueReviews(LocalDate today) {
        List<Long> reachableUserIds = pushRegistrationRepository.findDistinctUserIdsWithEnabledRegistration();
        if (reachableUserIds.isEmpty()) {
            return;
        }

        Map<Long, List<Subscription>> activeByUser = subscriptionRepository.findActiveByUserIds(reachableUserIds).stream()
                .collect(Collectors.groupingBy(s -> s.getUser().getId()));
        Map<Long, User> userById = userRepository.findAllById(reachableUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        for (Long userId : reachableUserIds) {
            User user = userById.get(userId);
            if (user == null || user.getCreatedAt() == null) {
                continue;
            }
            List<Subscription> subscriptions = activeByUser.getOrDefault(userId, List.of());
            boolean duplicate = hasCategoryDuplicate(subscriptions); // 발송 판정·문구 구성에 함께 쓰이므로 1회만 계산
            if (isDueToday(user, duplicate, today)) {
                notificationSender.send(userId, toDraft(duplicate, today));
            }
        }
    }

    private boolean isDueToday(User user, boolean duplicate, LocalDate today) {
        long daysSinceSignup = ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), today);
        if (daysSinceSignup <= 0) {
            return false; // 가입 당일/미래 기준일은 대상 아님
        }
        int period = duplicate ? PERIOD_WITH_DUPLICATE_DAYS : PERIOD_DEFAULT_DAYS;
        return daysSinceSignup % period == 0;
    }

    /** 활성 구독 중 같은 카테고리가 2개 이상이면 중복으로 본다. */
    private boolean hasCategoryDuplicate(List<Subscription> subscriptions) {
        return subscriptions.stream()
                .collect(Collectors.groupingBy(Subscription::getCategory, Collectors.counting()))
                .values().stream()
                .anyMatch(count -> count >= 2);
    }

    private NotificationDraft toDraft(boolean duplicate, LocalDate today) {
        Map<String, String> data = Map.of(
                "type", NotificationType.SUBSCRIPTION_REVIEW.name(),
                "link", frontendBaseUrl + REVIEW_PATH
        );
        return new NotificationDraft(
                NotificationType.SUBSCRIPTION_REVIEW,
                // targetDate = 발송일(오늘). 같은 날 재실행 시 dedup 키로 중복 발송 방지.
                today,
                0,
                duplicate ? "중복 구독 점검이 필요해요" : "구독 점검 알림",
                duplicate
                        ? "같은 카테고리에 중복된 구독이 있어요. 지금 점검해보세요."
                        : "구독 현황을 점검하고 불필요한 지출을 정리해보세요.",
                data);
    }
}
