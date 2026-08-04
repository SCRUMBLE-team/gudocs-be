package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialAccount;
import com.scrumble.gudocs.users.entity.SocialProvider;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SocialLoginProcessorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private SocialLoginProcessor socialLoginProcessor;

    private Map<String, Object> googleAttrs(String sub, String email) {
        return Map.of("sub", sub, "email", email, "email_verified", true, "name", "구글");
    }

    private Map<String, Object> kakaoAttrs(long id, String email) {
        return Map.of("id", id, "kakao_account", Map.of(
                "email", email, "is_email_verified", true,
                "profile", Map.of("nickname", "카카오")));
    }

    @Test
    void 기존_소셜계정_재로그인시_동일_user를_반환하고_새로_만들지_않는다() {
        User user = User.builder().id(1L).email("a@example.com").build();
        SocialAccount account = SocialAccount.builder()
                .user(user).provider(SocialProvider.GOOGLE).providerId("g-1")
                .email("a@example.com").emailVerified(true).build();
        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.GOOGLE, "g-1"))
                .willReturn(Optional.of(account));

        Long userId = socialLoginProcessor.login(SocialProvider.GOOGLE, googleAttrs("g-1", "a@example.com"));

        assertThat(userId).isEqualTo(1L);
        assertThat(account.getLastLoginAt()).isNotNull();
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void 처음_보는_소셜계정이면_user와_social_account를_생성한다() {
        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.GOOGLE, "g-1"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> User.builder().id(10L).email(inv.getArgument(0, User.class).getEmail()).build());
        given(socialAccountRepository.save(any(SocialAccount.class)))
                .willAnswer(inv -> inv.getArgument(0));

        Long userId = socialLoginProcessor.login(SocialProvider.GOOGLE, googleAttrs("g-1", "a@example.com"));

        assertThat(userId).isEqualTo(10L);
        // 신규 user는 이름을 비워두고(온보딩), 이메일만 저장
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isNull();
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("a@example.com");
    }

    @Test
    void 다른_provider가_같은_이메일이어도_이메일_조회_없이_별도_회원으로_가입한다() {
        // 카카오 계정(providerId 미존재)이 이미 다른 provider에 있는 이메일과 동일해도 차단하지 않는다
        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "999"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> User.builder().id(20L).email(inv.getArgument(0, User.class).getEmail()).build());
        given(socialAccountRepository.save(any(SocialAccount.class)))
                .willAnswer(inv -> inv.getArgument(0));

        Long userId = socialLoginProcessor.login(SocialProvider.KAKAO, kakaoAttrs(999L, "a@example.com"));

        assertThat(userId).isEqualTo(20L);
        // 이메일 충돌 검사(findByEmail)를 하지 않는다
        verify(userRepository, never()).findByEmail(any());
        ArgumentCaptor<SocialAccount> saCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(saCaptor.capture());
        assertThat(saCaptor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(saCaptor.getValue().getProviderId()).isEqualTo("999");
    }

    @Test
    void 이메일_미동의로_email이_없어도_email_null로_가입한다() {
        // 카카오 이메일 선택 동의 미동의 → kakao_account에 email 키 없음
        Map<String, Object> noEmail = Map.of("id", 777L, "kakao_account", Map.of(
                "profile", Map.of("nickname", "카카오")));
        given(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "777"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> User.builder().id(30L).email(inv.getArgument(0, User.class).getEmail()).build());
        given(socialAccountRepository.save(any(SocialAccount.class)))
                .willAnswer(inv -> inv.getArgument(0));

        Long userId = socialLoginProcessor.login(SocialProvider.KAKAO, noEmail);

        assertThat(userId).isEqualTo(30L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
    }
}
