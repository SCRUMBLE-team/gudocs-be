package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialAccount;
import com.scrumble.gudocs.users.entity.SocialProvider;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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
        OAuth2UserInfo info = OAuth2UserInfo.of(provider, attributes);

        if (info.email() == null || info.email().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_provided"),
                    "이메일 제공에 동의해야 로그인할 수 있습니다.");
        }

        SocialAccount account = socialAccountRepository
                .findByProviderAndProviderId(provider, info.providerId())
                .orElseGet(() -> register(provider, info));
        account.updateLastLoginAt(LocalDateTime.now());

        return account.getUser().getId();
    }

    /**
     * 처음 보는 소셜 계정이면 새 user를 만든다.
     * 동일 이메일이 이미 다른 provider로 가입돼 있으면 자동 병합하지 않고 막는다.
     * (계정 연결은 이후 단계에서 마이페이지로 처리)
     */
    private SocialAccount register(SocialProvider provider, OAuth2UserInfo info) {
        userRepository.findByEmail(info.email()).ifPresent(existing -> {
            String existingProvider = socialAccountRepository.findFirstByUser(existing)
                    .map(sa -> sa.getProvider().getDisplayName())
                    .orElse("다른 방식");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_already_registered"),
                    existingProvider + "로 이미 가입된 이메일입니다. 기존 로그인 방식을 사용해주세요.");
        });

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
