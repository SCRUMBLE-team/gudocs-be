package com.scrumble.gudocs.notification.entity;

public enum NotificationType {
    /** 결제 예정 알림 (D-3, 결제일 당일 / 같은 결제일 구독 묶음) */
    BILLING_REMINDER,
    /** 구독 검사 유도 알림 (회원 가입일 기준, 카테고리 중복 시 2주·아니면 4주 주기 반복) */
    SUBSCRIPTION_REVIEW
}
