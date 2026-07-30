package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.dto.response.PushRegistrationResponse;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import com.scrumble.gudocs.common.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PushRegistrationServiceTest {

    @Autowired
    private PushRegistrationService pushRegistrationService;
    @Autowired
    private PushRegistrationRepository pushRegistrationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;

    private User createUser(String email) {
        return TestSessions.createUser(userRepository, socialAccountRepository, "테스터", email);
    }

    private PushRegistrationRequest req(String fid) {
        return new PushRegistrationRequest(fid, PushPlatform.WEB, "Chrome on macOS");
    }

    @Test
    void 신규_FID_등록() {
        User user = createUser("a@e.com");

        PushRegistrationResponse response = pushRegistrationService.register(user.getId(), req("fid-1"));

        assertThat(response.registrationId()).isNotNull();
        assertThat(response.platform()).isEqualTo(PushPlatform.WEB);
        assertThat(response.enabled()).isTrue();
        assertThat(pushRegistrationRepository.findByFid("fid-1")).isPresent();
    }

    @Test
    void 동일_FID_재등록시_중복_생성_안함() {
        User user = createUser("b@e.com");

        PushRegistrationResponse first = pushRegistrationService.register(user.getId(), req("fid-2"));
        PushRegistrationResponse second = pushRegistrationService.register(user.getId(), req("fid-2"));

        assertThat(second.registrationId()).isEqualTo(first.registrationId());
        assertThat(pushRegistrationRepository.count()).isEqualTo(1);
    }

    @Test
    void 동일_FID_다른_사용자_등록시_소유자_변경() {
        User owner = createUser("c@e.com");
        User newOwner = createUser("d@e.com");

        pushRegistrationService.register(owner.getId(), req("fid-3"));
        pushRegistrationService.register(newOwner.getId(), req("fid-3"));

        assertThat(pushRegistrationRepository.count()).isEqualTo(1);
        PushRegistration reg = pushRegistrationRepository.findByFid("fid-3").orElseThrow();
        assertThat(reg.getUser().getId()).isEqualTo(newOwner.getId());
        assertThat(reg.isEnabled()).isTrue();
    }

    @Test
    void 등록_해제시_enabled_false() {
        User user = createUser("e@e.com");
        PushRegistrationResponse reg = pushRegistrationService.register(user.getId(), req("fid-4"));

        pushRegistrationService.unregister(user.getId(), reg.registrationId());

        assertThat(pushRegistrationRepository.findById(reg.registrationId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void 이미_비활성인_등록_해제도_성공_멱등() {
        User user = createUser("f@e.com");
        PushRegistrationResponse reg = pushRegistrationService.register(user.getId(), req("fid-5"));
        pushRegistrationService.unregister(user.getId(), reg.registrationId());

        // 두 번째 해제도 예외 없이 성공
        pushRegistrationService.unregister(user.getId(), reg.registrationId());

        assertThat(pushRegistrationRepository.findById(reg.registrationId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void 다른_사용자_등록_해제_차단() {
        User owner = createUser("g@e.com");
        User attacker = createUser("h@e.com");
        PushRegistrationResponse reg = pushRegistrationService.register(owner.getId(), req("fid-6"));

        assertThatThrownBy(() -> pushRegistrationService.unregister(attacker.getId(), reg.registrationId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PUSH_REGISTRATION_FORBIDDEN));
    }
}
