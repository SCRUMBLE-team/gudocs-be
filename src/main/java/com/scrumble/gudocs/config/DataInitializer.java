package com.scrumble.gudocs.config;

import com.scrumble.gudocs.billing.service.BillingRecordService;
import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.SocialAccount;
import com.scrumble.gudocs.users.entity.SocialProvider;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordService billingRecordService;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("test@test.com")) return;

        User user = userRepository.save(User.builder()
                .name("테스트 유저")
                .email("test@test.com")
                .build());

        socialAccountRepository.save(SocialAccount.builder()
                .user(user)
                .provider(SocialProvider.GOOGLE)
                .providerId("mock-google-1")
                .email("test@test.com")
                .emailVerified(true)
                .build());

        List<Subscription> subscriptions = subscriptionRepository.saveAll(List.of(
                Subscription.builder()
                        .user(user).serviceName("Netflix")
                        .category(SubscriptionCategory.OTT).price(17000L)
                        .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 5))
                        .build(),

                Subscription.builder()
                        .user(user).serviceName("YouTube Premium")
                        .category(SubscriptionCategory.OTT).price(14900L)
                        .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 10))
                        .build(),

                Subscription.builder()
                        .user(user).serviceName("Spotify")
                        .category(SubscriptionCategory.MUSIC).price(10900L)
                        .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 15))
                        .build(),

                Subscription.builder()
                        .user(user).serviceName("iCloud+")
                        .category(SubscriptionCategory.CLOUD).price(3900L)
                        .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 1))
                        .build(),

                Subscription.builder()
                        .user(user).serviceName("Google One")
                        .category(SubscriptionCategory.CLOUD).price(2900L)
                        .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 8))
                        .build(),

                Subscription.builder()
                        .user(user).serviceName("ChatGPT Plus")
                        .category(SubscriptionCategory.AI).price(24000L)
                        .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 20))
                        .build(),

                Subscription.builder()
                        .user(user).serviceName("Adobe Creative Cloud")
                        .category(SubscriptionCategory.DESIGN).price(624000L)
                        .billingCycle(BillingCycle.YEARLY).firstBillingDate(LocalDate.of(2025, 3, 1))
                        
                        .status(SubscriptionStatus.PAUSED)
                        .pausedAt(LocalDateTime.now()).build(),

                Subscription.builder()
                        .user(user).serviceName("인프런")
                        .category(SubscriptionCategory.EDUCATION).price(29000L)
                        .billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 25))
                        .build()
        ));

        // mock 구독의 과거 청구 일정 스냅샷. 지출 분석은 billing_records 만 읽으므로 이게 없으면 local 에서
        // 지출 화면이 통째로 비어 보인다. 정지 상태인 Adobe 는 제외 — 정지 구독은 결제되지 않는다.
        LocalDate today = LocalDate.now();
        subscriptions.stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .forEach(s -> billingRecordService.backfillPastBillings(s, today));
    }
}
