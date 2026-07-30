package com.scrumble.gudocs.notification.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Firebase Admin SDK 기반 실제 FCM 발송기. app.firebase.enabled=true 일 때만 활성화된다.
 */
@Component
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public PushResult send(String fid, PushMessage message) {
        // firebase-admin 9.10.0+: FID 발송은 setFid 사용. setToken은 legacy registration token
        // 호환용으로 deprecated 되었으므로 신규 구현에서는 사용하지 않는다.
        Message fcmMessage = Message.builder()
                .setFid(fid)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
        try {
            firebaseMessaging.send(fcmMessage);
            return PushResult.SUCCESS;
        } catch (FirebaseMessagingException e) {
            if (isInvalidToken(e.getMessagingErrorCode())) {
                log.info("무효 FID 발송 실패 (비활성화 대상) fid={} code={}", mask(fid), e.getMessagingErrorCode());
                return PushResult.INVALID_TOKEN;
            }
            log.warn("FCM 발송 실패 fid={} code={}", mask(fid), e.getMessagingErrorCode());
            return PushResult.FAILED;
        }
    }

    private boolean isInvalidToken(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }

    /** fid 전체 값을 로그에 남기지 않는다. */
    private String mask(String fid) {
        if (fid == null || fid.length() <= 6) {
            return "***";
        }
        return fid.substring(0, 6) + "***";
    }
}
