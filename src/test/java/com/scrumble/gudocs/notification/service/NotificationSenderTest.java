package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushResult;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSenderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);
    private static final Long USER_ID = 1L;

    @Mock
    private UserNotificationRepository userNotificationRepository;
    @Mock
    private PushRegistrationRepository pushRegistrationRepository;
    @Mock
    private PushSender pushSender;

    @InjectMocks
    private NotificationSender notificationSender;

    private NotificationDraft draft() {
        return new NotificationDraft(
                NotificationType.BILLING_REMINDER, TODAY, 0, "제목", "본문",
                Map.of("type", "BILLING_REMINDER", "link", "https://x/notifications"));
    }

    private PushRegistration registration(Long id, String fid) {
        return PushRegistration.builder()
                .id(id).fid(fid).platform(PushPlatform.WEB).enabled(true).build();
    }

    private void givenNoExistingHistory() {
        given(userNotificationRepository.findByUserIdAndTypeAndTargetDateAndRemindOffset(
                eq(USER_ID), eq(NotificationType.BILLING_REMINDER), any(), eq(0)))
                .willReturn(Optional.empty());
        given(userNotificationRepository.saveAndFlush(any(UserNotification.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 활성_FID_여러개에_각각_발송() {
        givenNoExistingHistory();
        given(pushRegistrationRepository.findByUserIdAndEnabledTrue(USER_ID))
                .willReturn(List.of(registration(1L, "fid-A"), registration(2L, "fid-B")));
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.SUCCESS);

        notificationSender.send(USER_ID, draft());

        verify(pushSender).send(eq("fid-A"), any(PushMessage.class));
        verify(pushSender).send(eq("fid-B"), any(PushMessage.class));
        verify(userNotificationRepository).save(any(UserNotification.class)); // sentAt 기록
    }

    @Test
    void 한_FID_실패해도_다른_FID는_계속_발송() {
        givenNoExistingHistory();
        given(pushRegistrationRepository.findByUserIdAndEnabledTrue(USER_ID))
                .willReturn(List.of(registration(1L, "fid-A"), registration(2L, "fid-B")));
        given(pushSender.send(eq("fid-A"), any(PushMessage.class))).willReturn(PushResult.FAILED);
        given(pushSender.send(eq("fid-B"), any(PushMessage.class))).willReturn(PushResult.SUCCESS);

        notificationSender.send(USER_ID, draft());

        verify(pushSender).send(eq("fid-A"), any(PushMessage.class));
        verify(pushSender).send(eq("fid-B"), any(PushMessage.class));
    }

    @Test
    void 무효_FID는_비활성화() {
        givenNoExistingHistory();
        PushRegistration invalid = registration(1L, "fid-A");
        given(pushRegistrationRepository.findByUserIdAndEnabledTrue(USER_ID))
                .willReturn(List.of(invalid));
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.INVALID_TOKEN);

        notificationSender.send(USER_ID, draft());

        assertThat(invalid.isEnabled()).isFalse();
        verify(pushRegistrationRepository).save(invalid);
        verify(userNotificationRepository, never()).save(any()); // 성공 발송 없음 → sentAt 미기록
    }

    @Test
    void 이미_발송_성공한_이력은_건너뜀() {
        UserNotification alreadySent = UserNotification.builder()
                .userId(USER_ID).type(NotificationType.BILLING_REMINDER).remindOffset(0)
                .title("t").body("b").targetDate(TODAY).build();
        alreadySent.markSent(LocalDateTime.of(2026, 5, 10, 9, 0)); // sentAt != null
        given(userNotificationRepository.findByUserIdAndTypeAndTargetDateAndRemindOffset(
                eq(USER_ID), eq(NotificationType.BILLING_REMINDER), any(), eq(0)))
                .willReturn(Optional.of(alreadySent));

        notificationSender.send(USER_ID, draft());

        verify(userNotificationRepository, never()).saveAndFlush(any());
        verify(pushSender, never()).send(anyString(), any());
        verify(pushRegistrationRepository, never()).findByUserIdAndEnabledTrue(anyLong());
    }

    @Test
    void 미발송_이력이_있으면_기존_행을_재사용해_재발송() {
        UserNotification pending = UserNotification.builder()
                .userId(USER_ID).type(NotificationType.BILLING_REMINDER).remindOffset(0)
                .title("t").body("b").targetDate(TODAY).build(); // sentAt == null
        given(userNotificationRepository.findByUserIdAndTypeAndTargetDateAndRemindOffset(
                eq(USER_ID), eq(NotificationType.BILLING_REMINDER), any(), eq(0)))
                .willReturn(Optional.of(pending));
        given(pushRegistrationRepository.findByUserIdAndEnabledTrue(USER_ID))
                .willReturn(List.of(registration(1L, "fid-A")));
        given(pushSender.send(anyString(), any(PushMessage.class))).willReturn(PushResult.SUCCESS);

        notificationSender.send(USER_ID, draft());

        // 새로 만들지 않고(saveAndFlush 미호출) 기존 행 재사용, 재발송 후 sentAt 기록(save)
        verify(userNotificationRepository, never()).saveAndFlush(any());
        verify(pushSender).send(eq("fid-A"), any(PushMessage.class));
        verify(userNotificationRepository).save(pending);
    }
}
