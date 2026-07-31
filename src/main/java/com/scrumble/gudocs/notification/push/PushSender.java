package com.scrumble.gudocs.notification.push;

/**
 * 푸시 발송 추상화. Firebase 활성/비활성에 따라 구현이 갈린다.
 * ({@link FcmPushSender} / {@link NoopPushSender})
 */
public interface PushSender {

    /** fid는 대상 기기의 Firebase Installation ID. */
    PushResult send(String fid, PushMessage message);
}
