package com.scrumble.gudocs.notification.push;

/**
 * 푸시 발송 추상화. Firebase 활성/비활성에 따라 구현이 갈린다.
 * ({@link FcmPushSender} / {@link NoopPushSender})
 */
public interface PushSender {

    /**
     * @param fid     대상 기기 토큰 (FE가 등록한 값)
     * @param message 알림 페이로드
     * @return 발송 결과
     */
    PushResult send(String fid, PushMessage message);
}
