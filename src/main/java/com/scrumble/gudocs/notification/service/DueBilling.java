package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.subscriptions.entity.Subscription;

import java.time.LocalDate;

/**
 * 결제 예정 대상 한 건. (구독 + 다음 결제일 + 남은 일수)
 */
public record DueBilling(Subscription subscription, LocalDate targetDate, int daysUntil) {
}
