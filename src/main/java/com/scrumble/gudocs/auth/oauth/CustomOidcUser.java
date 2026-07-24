package com.scrumble.gudocs.auth.oauth;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * OIDC(Google) 세션 principal. user.id를 함께 보유한다.
 */
public class CustomOidcUser extends DefaultOidcUser implements UserPrincipal {

    private final Long userId;

    public CustomOidcUser(Long userId, OidcIdToken idToken, OidcUserInfo userInfo) {
        super(java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                idToken, userInfo);
        this.userId = userId;
    }

    @Override
    public Long getUserId() {
        return userId;
    }
}
