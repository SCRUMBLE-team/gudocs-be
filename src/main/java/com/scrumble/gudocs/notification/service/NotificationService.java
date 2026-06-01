package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.notification.dto.response.UpcomingNotification;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int WINDOW_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UpcomingNotification> findUpcoming(String email) {
        return findUpcoming(email, LocalDate.now());
    }

    @Transactional(readOnly = true)
    List<UpcomingNotification> findUpcoming(String email, LocalDate today) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Subscription> active = subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .toList();

        return calculate(active, today);
    }

    public List<UpcomingNotification> calculate(List<Subscription> activeSubscriptions, LocalDate today) {
        LocalDate threshold = today.plusDays(WINDOW_DAYS);

        return activeSubscriptions.stream()
                .flatMap(s -> {
                    LocalDate nextBillingDate = NextBillingDateCalculator.calculate(s, today);
                    if (!nextBillingDate.isBefore(today) && !nextBillingDate.isAfter(threshold)) {
                        int daysUntil = (int) ChronoUnit.DAYS.between(today, nextBillingDate);
                        return Stream.of(new UpcomingNotification(
                                s.getId(), s.getServiceName(), s.getPrice(), nextBillingDate, daysUntil));
                    }
                    return Stream.empty();
                })
                .sorted(Comparator.comparing(UpcomingNotification::nextBillingDate))
                .toList();
    }
}
