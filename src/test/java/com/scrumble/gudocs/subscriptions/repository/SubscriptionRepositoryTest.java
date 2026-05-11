package com.scrumble.gudocs.subscriptions.repository;

import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.users.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SubscriptionRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private User savedUser(String email) {
        User user = User.builder()
                .name("테스터")
                .email(email)
                .passwordHash("hashed")
                .build();
        return em.persistAndFlush(user);
    }

    private Subscription savedSubscription(User user, String serviceName) {
        Subscription subscription = Subscription.builder()
                .user(user)
                .serviceName(serviceName)
                .category(SubscriptionCategory.OTT)
                .price(17000L)
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .paymentMethod(PaymentMethod.CARD)
                .build();
        return em.persistAndFlush(subscription);
    }

    @Test
    void findAllByUserOrderByCreatedAtDesc_성공() {
        User user = savedUser("test@example.com");
        savedSubscription(user, "Netflix");
        savedSubscription(user, "YouTube");

        List<Subscription> result = subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Subscription::getServiceName)
                .containsExactlyInAnyOrder("Netflix", "YouTube");
    }

    @Test
    void findAllByUserOrderByCreatedAtDesc_다른_사용자_구독_미포함() {
        User user1 = savedUser("user1@example.com");
        User user2 = savedUser("user2@example.com");
        savedSubscription(user1, "Netflix");
        savedSubscription(user2, "YouTube");

        List<Subscription> result = subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceName()).isEqualTo("Netflix");
    }

    @Test
    void findAllByUserOrderByCreatedAtDesc_구독_없으면_빈_목록() {
        User user = savedUser("empty@example.com");

        List<Subscription> result = subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user);

        assertThat(result).isEmpty();
    }

    @Test
    void 기본_상태는_ACTIVE() {
        User user = savedUser("active@example.com");
        Subscription subscription = savedSubscription(user, "Netflix");

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
