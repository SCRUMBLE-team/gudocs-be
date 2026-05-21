package com.scrumble.gudocs.subscriptions.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import com.scrumble.gudocs.users.entity.User;
import jakarta.persistence.*;
import lombok.*;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionCategory category;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "billing_day", nullable = false)
    private Integer billingDay;

    @Column(name = "billing_month")
    private Integer billingMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void update(String serviceName, SubscriptionCategory category, Long price,
                       BillingCycle billingCycle, Integer billingDay, Integer billingMonth,
                       PaymentMethod paymentMethod) {
        if (serviceName != null) this.serviceName = serviceName;
        if (category != null) this.category = category;
        if (price != null) this.price = price;
        if (billingCycle != null) this.billingCycle = billingCycle;
        if (billingDay != null) this.billingDay = billingDay;
        if (billingMonth != null) this.billingMonth = billingMonth;
        if (paymentMethod != null) this.paymentMethod = paymentMethod;
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
