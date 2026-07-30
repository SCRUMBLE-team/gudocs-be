package com.scrumble.gudocs.notification.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import com.scrumble.gudocs.users.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자의 푸시 수신 브라우저(기기) 등록 정보.
 * fid는 전역 UNIQUE — 동일 fid 재등록 시 새 행을 만들지 않고 소유자/상태를 갱신한다.
 */
@Entity
@Table(
        name = "push_registrations",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_registrations_fid", columnNames = "fid")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PushRegistration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String fid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PushPlatform platform = PushPlatform.WEB;

    @Column(name = "device_name")
    private String deviceName;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;

    /**
     * 동일 fid 재등록: 소유자를 현재 사용자로 (재)연결하고 활성화한다.
     */
    public void reassignTo(User user, PushPlatform platform, String deviceName) {
        this.user = user;
        this.platform = platform != null ? platform : PushPlatform.WEB;
        this.deviceName = deviceName;
        this.enabled = true;
        this.lastRegisteredAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
    }
}
