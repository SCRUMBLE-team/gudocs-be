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
 *
 * <p><b>소유권 이전은 의도된 정책이다.</b> fid는 Firebase Installation ID로 "브라우저 설치" 하나를
 * 가리킨다. 공용 PC에서 A가 등록 후 로그아웃하고 B가 같은 브라우저로 로그인해 같은 fid를 등록하면,
 * 그 브라우저는 이제 B의 것이므로 소유권을 B로 옮겨야 한다. 만약 A의 등록을 남겨두면(예: (user_id, fid)
 * 복합 UNIQUE로 두 행 공존) A의 결제 알림이 현재 B가 쓰는 브라우저로 전달되어 정보가 노출된다.
 * 따라서 fid는 전역 UNIQUE로 두고 재등록 시 현재 로그인 사용자로 소유권을 이전한다.
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
     * 소유권 이전이 의도된 정책인 이유는 클래스 주석 참고(공용 브라우저 정보 노출 방지).
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
