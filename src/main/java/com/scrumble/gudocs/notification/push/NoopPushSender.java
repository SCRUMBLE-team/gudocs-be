package com.scrumble.gudocs.notification.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Firebase 비활성(local/test/미설정) 환경용 no-op 발송기.
 * 실제 발송 없이 성공으로 처리한다. app.firebase.enabled 미설정/false 일 때 활성화된다.
 */
@Component
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(NoopPushSender.class);

    @Override
    public PushResult send(String fid, PushMessage message) {
        log.debug("Firebase 비활성 — 실제 발송 생략 (title={})", message.title());
        return PushResult.SUCCESS;
    }
}
