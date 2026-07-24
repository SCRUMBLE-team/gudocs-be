package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * OIDC provider = Google 로그인 처리. (scope에 openid 포함 시 Spring이 이 경로를 탄다)
 */
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final SocialLoginProcessor socialLoginProcessor;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        SocialProvider provider =
                SocialProvider.from(userRequest.getClientRegistration().getRegistrationId());
        Long userId = socialLoginProcessor.login(provider, oidcUser.getAttributes());
        return new CustomOidcUser(userId, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
