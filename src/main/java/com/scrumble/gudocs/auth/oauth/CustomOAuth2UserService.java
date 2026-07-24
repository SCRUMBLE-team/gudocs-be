package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialAccount;
import com.scrumble.gudocs.users.entity.SocialProvider;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        SocialProvider provider = SocialProvider.from(registrationId);
        OAuth2UserInfo info = OAuth2UserInfo.of(provider, oAuth2User.getAttributes());

        if (info.email() == null || info.email().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_provided"),
                    "이메일 제공에 동의해야 로그인할 수 있습니다.");
        }

        SocialAccount account = socialAccountRepository
                .findByProviderAndProviderId(provider, info.providerId())
                .orElseGet(() -> register(provider, info));
        account.updateLastLoginAt(LocalDateTime.now());

        return new CustomOAuth2User(account.getUser().getId(), oAuth2User.getAttributes());
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

        User user = userRepository.save(User.builder()
                .name(info.name())
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
