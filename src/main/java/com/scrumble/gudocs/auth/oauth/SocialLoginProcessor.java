package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialAccount;
import com.scrumble.gudocs.users.entity.SocialProvider;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OAuth2(Kakao/Naver)와 OIDC(Google) 로그인이 공유하는 소셜 계정 처리 단일 소스.
 * provider별 userinfo를 정규화하고, 소셜 계정을 조회/생성한 뒤 user.id를 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class SocialLoginProcessor {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public Long login(SocialProvider provider, Map<String, Object> attributes) {
        // provider+providerId로 식별하므로 이메일은 필수가 아니다.
        // 카카오 등 이메일 선택 동의 provider는 미동의 시 email이 null일 수 있다.
        OAuth2UserInfo info = OAuth2UserInfo.of(provider, attributes);

        SocialAccount account = socialAccountRepository
                .findByProviderAndProviderId(provider, info.providerId())
                .orElseGet(() -> register(provider, info));
        account.updateLastLoginAt(LocalDateTime.now());

        return account.getUser().getId();
    }

    /**
     * 처음 보는 소셜 계정이면 새 user를 만든다.
     * provider+providerId로 식별하므로, 다른 제공자가 같은 이메일이어도 별도 회원으로 가입한다.
     */
    private SocialAccount register(SocialProvider provider, OAuth2UserInfo info) {
        // 이름은 온보딩 화면에서 입력받으므로 최초에는 비워둔다
        User user = userRepository.save(User.builder()
                .email(info.email())
                .build());

        return socialAccountRepository.save(SocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(info.providerId())
                .email(info.email())
                .emailVerified(info.emailVerified())
                .build());
    }
}
