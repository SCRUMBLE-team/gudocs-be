package com.scrumble.gudocs.subscriptions.repository;

import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("SELECT s FROM Subscription s WHERE s.user = :user AND s.deletedAt IS NULL " +
            "ORDER BY s.createdAt DESC")
    List<Subscription> findAllByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT s FROM Subscription s WHERE s.user = :user ORDER BY s.createdAt DESC")
    List<Subscription> findAllByUserIncludingDeleted(@Param("user") User user);

    boolean existsByUserAndServiceNameIgnoreCaseAndDeletedAtIsNull(User user, String serviceName);

    @Modifying
    @Transactional
    @Query("UPDATE Subscription s SET s.deletedAt = CURRENT_TIMESTAMP WHERE s.user = :user AND s.deletedAt IS NULL")
    int softDeleteAllByUser(@Param("user") User user);
}
