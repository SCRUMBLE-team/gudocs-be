package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;

import java.time.LocalDate;
import java.util.Map;

/**
 * 발송할 알림 1건의 내용. 중복 방지 키(type+targetDate+remindOffset)와 표시 문구, FCM data를 담는다.
 * 결제 알림·검사 유도 등 서로 다른 발송 배치가 공통 {@link NotificationSender}로 발송하기 위한 입력.
 *
 * @param type         알림 종류 (dedup 키)
 * @param targetDate   대상 날짜 (dedup 키) — 결제 알림은 결제일, 검사 유도는 발송일(오늘)
 * @param remindOffset 발송 단계 discriminator (dedup 키) — 결제 알림은 결제 며칠 전(3/0), 그 외 0
 * @param title        푸시 제목
 * @param body         푸시 본문
 * @param pushData     FCM data 페이로드 (type, link 등)
 */
public record NotificationDraft(
        NotificationType type,
        LocalDate targetDate,
        int remindOffset,
        String title,
        String body,
        Map<String, String> pushData) {
}
