package com.scrumble.gudocs.users.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_social_provider_provider_id",
                        columnNames = {"provider", "provider_id"}),
                @UniqueConstraint(name = "uk_social_user_provider",
                        columnNames = {"user_id", "provider"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SocialAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    // 이메일 선택 동의 provider(카카오 등) 미동의 시 null 가능
    @Column
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public void updateLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
