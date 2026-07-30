package com.scrumble.gudocs.notification.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FcmPushSenderTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FcmPushSender sender;

    private final PushMessage message = new PushMessage("제목", "본문", Map.of("type", "BILLING_REMINDER"));

    @Test
    void 발송_성공() throws Exception {
        given(firebaseMessaging.send(any(Message.class))).willReturn("msg-id");

        assertThat(sender.send("fid-123456789", message)).isEqualTo(PushResult.SUCCESS);
    }

    @Test
    void 발송_Message는_deprecated_token이_아니라_fid_대상으로_생성된다() throws Exception {
        given(firebaseMessaging.send(any(Message.class))).willReturn("msg-id");

        sender.send("fid-123456789", message);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(captor.capture());
        Message sent = captor.getValue();
        // Message는 fid/token을 private 필드로만 보관 → 리플렉션으로 발송 대상 필드 검증
        assertThat(ReflectionTestUtils.getField(sent, "fid")).isEqualTo("fid-123456789");
        assertThat(ReflectionTestUtils.getField(sent, "token")).isNull();
    }

    @Test
    void 무효_토큰이면_INVALID_TOKEN() throws Exception {
        FirebaseMessagingException ex = mock(MessagingErrorCode.UNREGISTERED);
        given(firebaseMessaging.send(any(Message.class))).willThrow(ex);

        assertThat(sender.send("fid-123456789", message)).isEqualTo(PushResult.INVALID_TOKEN);
    }

    @Test
    void 일시적_실패면_FAILED() throws Exception {
        FirebaseMessagingException ex = mock(MessagingErrorCode.UNAVAILABLE);
        given(firebaseMessaging.send(any(Message.class))).willThrow(ex);

        assertThat(sender.send("fid-123456789", message)).isEqualTo(PushResult.FAILED);
    }

    private FirebaseMessagingException mock(MessagingErrorCode code) {
        FirebaseMessagingException ex = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        given(ex.getMessagingErrorCode()).willReturn(code);
        return ex;
    }
}
