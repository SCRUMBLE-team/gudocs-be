package com.scrumble.gudocs.notification.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 발송 이력. 중복 발송 방지를 위한 멱등 키를 보유한다.
 * UNIQUE(user_id, subscription_id, type, target_date) 로 동일 결제 예정일 중복 발송을 막는다.
 * (userId/subscriptionId는 연관관계 대신 값으로 저장 — 발송 배치에서 지연 로딩 이슈 없이 단순 처리)
 */
@Entity
@Table(
        name = "user_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_notifications_dedup",
                columnNames = {"user_id", "subscription_id", "type", "target_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserNotification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public void markSent(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
