package com.scrumble.gudocs.config;

import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("test@test.com")) return;

        User user = userRepository.save(User.builder()
                .name("테스트 유저")
                .email("test@test.com")
                .passwordHash(passwordEncoder.encode("Test1234!"))
                .build());

        subscriptionRepository.saveAll(List.of(
                Subscription.builder()
                        .user(user).serviceName("Netflix")
                        .category(SubscriptionCategory.OTT).price(17000L)
                        .billingCycle(BillingCycle.MONTHLY).billingDay(5)
                        .paymentMethod(PaymentMethod.CARD).build(),

                Subscription.builder()
                        .user(user).serviceName("YouTube Premium")
                        .category(SubscriptionCategory.OTT).price(14900L)
                        .billingCycle(BillingCycle.MONTHLY).billingDay(10)
                        .paymentMethod(PaymentMethod.CARD).build(),

                Subscription.builder()
                        .user(user).serviceName("Spotify")
                        .category(SubscriptionCategory.MUSIC).price(10900L)
                        .billingCycle(BillingCycle.MONTHLY).billingDay(15)
                        .paymentMethod(PaymentMethod.SIMPLE_PAY).build(),

                Subscription.builder()
                        .user(user).serviceName("iCloud+")
                        .category(SubscriptionCategory.CLOUD).price(3900L)
                        .billingCycle(BillingCycle.MONTHLY).billingDay(1)
                        .paymentMethod(PaymentMethod.CARD).build(),

                Subscription.builder()
                        .user(user).serviceName("Google One")
                        .category(SubscriptionCategory.CLOUD).price(2900L)
                        .billingCycle(BillingCycle.MONTHLY).billingDay(8)
                        .paymentMethod(PaymentMethod.CARD).build(),

                Subscription.builder()
                        .user(user).serviceName("ChatGPT Plus")
                        .category(SubscriptionCategory.AI).price(24000L)
                        .billingCycle(BillingCycle.MONTHLY).billingDay(20)
                        .paymentMethod(PaymentMethod.CARD).build(),

                Subscription.builder()
                        .user(user).serviceName("Adobe Creative Cloud")
                        .category(SubscriptionCategory.DESIGN).price(624000L)
                        .billingCycle(BillingCycle.YEARLY).billingDay(1).billingMonth(3)
                        .paymentMethod(PaymentMethod.CARD)
                        .status(SubscriptionStatus.PAUSED)
                        .pausedAt(LocalDateTime.now()).build(),

                Subscription.builder()
                        .user(user).serviceName("인프런")
                        .category(SubscriptionCategory.EDUCATION).price(29000L)
                        .billingCycle(BillingCycle.MONTHLY).billingDay(25)
                        .paymentMethod(PaymentMethod.CARD).build()
        ));
    }
}
