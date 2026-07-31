package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.subscriptions.entity.Subscription;

import java.time.LocalDate;

public record DueBilling(Subscription subscription, LocalDate targetDate, int daysUntil) {
}
