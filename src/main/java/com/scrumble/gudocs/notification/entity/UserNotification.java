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
 * UNIQUE(user_id, type, target_date, remind_offset, subscription_id) 로 동일 발송 단계 중복 발송을 막는다.
 * <ul>
 *   <li>결제 알림은 같은 결제일 구독을 묶어 1건 발송하므로 구독을 특정할 수 없다({@link #NO_SUBSCRIPTION}).
 *       같은 결제일(target_date)에 D-3/당일 두 번 발송되므로 remind_offset(3/0)로 단계를 구분한다.</li>
 *   <li>검사 유도 알림은 유저 단위라 구독이 없고({@link #NO_SUBSCRIPTION}), remind_offset=0 이다.</li>
 *   <li>해지 알림은 클릭 시 구독 상세로 보내야 해서 <b>구독별 1건</b>으로 발송한다. 그래서 dedup 키에
 *       subscription_id 가 들어간다 — 같은 날 같은 단계의 서로 다른 구독을 구분해야 하기 때문이다.</li>
 * </ul>
 * (userId/subscriptionId는 연관관계 대신 값으로 저장 — 발송 배치에서 지연 로딩 이슈 없이 단순 처리)
 */
@Entity
@Table(
        name = "user_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_notifications_dedup",
                columnNames = {"user_id", "type", "target_date", "remind_offset", "subscription_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserNotification extends BaseEntity {

    /**
     * "특정 구독 없음"을 뜻하는 subscription_id 값. NULL 을 쓰지 않는 이유는 MySQL UNIQUE 인덱스가
     * NULL 을 서로 다른 값으로 취급해, 묶음 알림의 중복 방지가 풀려버리기 때문이다.
     */
    public static final long NO_SUBSCRIPTION = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 알림이 가리키는 구독. 묶음·유저 단위 알림이면 {@link #NO_SUBSCRIPTION}. */
    @Column(name = "subscription_id", nullable = false)
    private long subscriptionId;

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
