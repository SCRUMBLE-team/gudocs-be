package com.scrumble.gudocs.notification.entity;

public enum NotificationType {
    /** 결제 예정 알림 (D-3, 결제일 당일 / 같은 결제일 구독 묶음) */
    BILLING_REMINDER,
    /** 구독 검사 유도 알림 (회원 가입일 기준, 카테고리 중복 시 2주·아니면 4주 주기 반복) */
    SUBSCRIPTION_REVIEW,
    /**
     * 해지 알림 (절약 후보로 체크한 구독의 D-3 — 결제 예정 알림을 대체).
     * 클릭 시 해당 구독 상세로 보내야 해서 묶지 않고 구독별 1건으로 발송한다.
     */
    CANCEL_REMINDER
}
