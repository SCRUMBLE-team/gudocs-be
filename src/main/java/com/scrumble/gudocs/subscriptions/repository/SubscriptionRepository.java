package com.scrumble.gudocs.subscriptions.repository;

import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
