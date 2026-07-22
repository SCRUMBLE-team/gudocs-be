package com.scrumble.gudocs.auth.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 세션 principal. 로그인 수단(provider)과 무관하게 user.id를 기준 식별자로 보유한다.
 */
public class CustomOAuth2User implements OAuth2User {

    private final Long userId;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(Long userId, Map<String, Object> attributes) {
        this.userId = userId;
        this.attributes = attributes;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
