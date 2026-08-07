package com.scrumble.gudocs.subscriptions.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import com.scrumble.gudocs.users.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    /**
     * 카탈로그 서비스의 불변 키({@code ServiceCatalog.code}). 프론트가 로고를 찾는 기준.
     * 카탈로그에 없는 서비스를 직접 입력해 등록하면 null 이다.
     */
    @Column(name = "service_code", length = 64)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionCategory category;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "first_billing_date", nullable = false)
    private LocalDate firstBillingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void update(String serviceName, String serviceCode, SubscriptionCategory category, Long price,
                       BillingCycle billingCycle, LocalDate firstBillingDate) {
        this.serviceName = serviceName;
        this.serviceCode = serviceCode;
        this.category = category;
        this.price = price;
        this.billingCycle = billingCycle;
        this.firstBillingDate = firstBillingDate;
    }

    public void updateStatus(SubscriptionStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        if (this.status == status) return;

        if (status == SubscriptionStatus.PAUSED) {
            this.pausedAt = LocalDateTime.now();
        } else if (status == SubscriptionStatus.ACTIVE) {
            this.pausedAt = null;
        }
        this.status = status;
    }

    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
