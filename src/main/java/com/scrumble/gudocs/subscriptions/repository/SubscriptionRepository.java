package com.scrumble.gudocs.subscriptions.repository;

import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByUserOrderByCreatedAtDesc(User user);
}
