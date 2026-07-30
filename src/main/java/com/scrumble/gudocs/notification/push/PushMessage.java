package com.scrumble.gudocs.notification.push;

import java.util.Map;

/**
 * 발송 대상 무관한 알림 페이로드. data 값은 모두 문자열이다.
 */
public record PushMessage(String title, String body, Map<String, String> data) {
}
