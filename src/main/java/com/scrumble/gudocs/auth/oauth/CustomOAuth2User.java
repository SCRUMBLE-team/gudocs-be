package com.scrumble.gudocs.auth.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 세션 principal. 로그인 수단(provider)과 무관하게 user.id를 기준 식별자로 보유한다.
 *
 * <p><b>Serializable 필수.</b> 세션은 Spring Session JDBC로 DB에 저장되고, 저장 시
 * {@code SecurityContext} 전체가 Java 직렬화되어 {@code SPRING_SESSION_ATTRIBUTES}에 들어간다.
 * 이 클래스가 구현하는 {@link OAuth2User}는 {@link Serializable}을 상속하지 않으므로
 * 여기서 직접 붙여야 한다 — 빠지면 로그인 성공 직후 {@code NotSerializableException}으로 500이 난다.
 * (OIDC용 {@link CustomOidcUser}는 상위 {@code DefaultOAuth2User}가 이미 Serializable이라 무사했고,
 * 그래서 구글만 정상 동작하고 카카오·네이버가 터졌다.)
 */
public class CustomOAuth2User implements OAuth2User, UserPrincipal, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(Long userId, Map<String, Object> attributes) {
        this.userId = userId;
        // provider가 넘긴 Map 구현체가 직렬화 가능하다는 보장이 없어 직렬화 가능한 복사본으로 고정한다.
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    @Override
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
