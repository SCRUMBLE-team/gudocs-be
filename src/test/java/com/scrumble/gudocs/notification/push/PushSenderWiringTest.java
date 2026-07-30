package com.scrumble.gudocs.notification.push;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Firebase 비활성(test 프로파일: app.firebase.enabled=false) 환경에서는
 * 실제 발송기(FcmPushSender)가 아니라 NoopPushSender가 주입되어야 한다.
 */
@SpringBootTest
class PushSenderWiringTest {

    @Autowired
    private PushSender pushSender;

    @Test
    void Firebase_비활성이면_NoopPushSender가_주입된다() {
        assertThat(pushSender).isInstanceOf(NoopPushSender.class);
    }

    @Test
    void Noop은_실제_발송없이_성공을_반환한다() {
        PushResult result = pushSender.send("fid", new PushMessage("t", "b", java.util.Map.of()));
        assertThat(result).isEqualTo(PushResult.SUCCESS);
    }
}
