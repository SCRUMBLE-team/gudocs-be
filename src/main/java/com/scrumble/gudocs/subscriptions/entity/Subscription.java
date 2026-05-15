package com.scrumble.gudocs.subscriptions.entity;

import com.scrumble.gudocs.users.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Subscription {

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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void update(String serviceName, SubscriptionCategory category, Long price,
                       BillingCycle billingCycle, Integer billingDay, Integer billingMonth,
                       PaymentMethod paymentMethod) {
        if (serviceName != null) this.serviceName = serviceName;
        if (category != null) this.category = category;
        if (price != null) this.price = price;
        if (billingCycle != null) this.billingCycle = billingCycle;
        if (billingDay != null) this.billingDay = billingDay;
        this.billingMonth = billingMonth;
        if (paymentMethod != null) this.paymentMethod = paymentMethod;
    }

    public void updateStatus(SubscriptionStatus status) {
        this.status = status;
    }
}
