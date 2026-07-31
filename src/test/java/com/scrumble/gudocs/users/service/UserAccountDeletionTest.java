package com.scrumble.gudocs.users.service;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.UserNotification;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import com.scrumble.gudocs.notification.service.PushRegistrationService;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserAccountDeletionTest {

    @Autowired
    private UserService userService;
    @Autowired
    private PushRegistrationService pushRegistrationService;
    @Autowired
    private PushRegistrationRepository pushRegistrationRepository;
    @Autowired
    private UserNotificationRepository userNotificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Test
    void 회원_탈퇴시_FID와_알림이력이_함께_삭제된다() {
        User user = TestSessions.createUser(userRepository, socialAccountRepository, "테스터", "del@example.com");
        pushRegistrationService.register(user.getId(),
                new PushRegistrationRequest("fid-del", PushPlatform.WEB, "Chrome"));
        userNotificationRepository.save(UserNotification.builder()
                .userId(user.getId()).subscriptionId(999L)
                .type(NotificationType.BILLING_REMINDER)
                .title("t").body("b").targetDate(LocalDate.now())
                .build());

        userService.deleteAccount(user.getId());

        assertThat(pushRegistrationRepository.findByFid("fid-del")).isEmpty();
        assertThat(userNotificationRepository.count()).isZero();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
