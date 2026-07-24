package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * OAuth2(비 OIDC) provider = Kakao/Naver 로그인 처리.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SocialLoginProcessor socialLoginProcessor;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        SocialProvider provider =
                SocialProvider.from(userRequest.getClientRegistration().getRegistrationId());
        Long userId = socialLoginProcessor.login(provider, oAuth2User.getAttributes());
        return new CustomOAuth2User(userId, oAuth2User.getAttributes());
    }
}
