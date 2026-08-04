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
 * UNIQUE(user_id, type, target_date, remind_offset) 로 동일 발송 단계 중복 발송을 막는다.
 * <ul>
 *   <li>결제 알림은 같은 결제일 구독을 묶어 1건 발송하므로 subscription_id 를 특정할 수 없다(NULL).
 *       같은 결제일(target_date)에 D-3/당일 두 번 발송되므로 remind_offset(3/0)로 단계를 구분한다.</li>
 *   <li>검사 유도 알림은 유저 단위라 subscription_id 가 없고(NULL), remind_offset=0 이다.</li>
 * </ul>
 * (userId/subscriptionId는 연관관계 대신 값으로 저장 — 발송 배치에서 지연 로딩 이슈 없이 단순 처리)
 */
@Entity
@Table(
        name = "user_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_notifications_dedup",
                columnNames = {"user_id", "type", "target_date", "remind_offset"})
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

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /**
     * 발송 단계 discriminator. 결제 알림의 결제 며칠 전(3=D-3, 0=당일)을 구분해 dedup 키에 포함한다.
     * 검사 유도 등 단계 개념이 없는 알림은 0.
     */
    @Column(name = "remind_offset", nullable = false)
    private int remindOffset;

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
