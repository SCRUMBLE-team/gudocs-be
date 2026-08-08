package com.scrumble.gudocs.auth.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 세션 principal의 Java 직렬화 가능 여부를 검증한다.
 *
 * <p>세션은 Spring Session JDBC로 DB에 저장되고, 그 과정에서 {@code SecurityContext} 전체가
 * Java 직렬화된다. principal이 Serializable이 아니면 <b>로그인 성공 직후 500</b>이 난다.
 * 실제로 {@link CustomOAuth2User}가 Serializable이 아니어서 카카오·네이버 로그인이 전부 실패했고
 * ({@code NotSerializableException}), 구글만 {@link CustomOidcUser}의 상위 클래스 덕에 살아 있었다.
 *
 * <p>이 테스트는 H2/MySQL 차이와 무관한 순수 직렬화 검사라 CI에서 그대로 재현된다.
 */
class SessionPrincipalSerializationTest {

    @Test
    @DisplayName("CustomOAuth2User(카카오·네이버)를 담은 SecurityContext는 직렬화된다")
    void customOAuth2UserIsSerializable() throws Exception {
        // 카카오 userinfo와 같은 중첩 Map 구조로 구성한다
        Map<String, Object> attributes = Map.of(
                "id", 1234567890L,
                "kakao_account", Map.of(
                        "email", "test@kakao.com",
                        "is_email_verified", true,
                        "profile", Map.of("nickname", "테스터")));

        CustomOAuth2User principal = new CustomOAuth2User(42L, attributes);
        SecurityContextImpl context = new SecurityContextImpl(
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao"));

        Object restored = roundTrip(context);

        assertThat(restored).isInstanceOf(SecurityContextImpl.class);
        Object restoredPrincipal = ((SecurityContextImpl) restored).getAuthentication().getPrincipal();
        assertThat(restoredPrincipal).isInstanceOf(CustomOAuth2User.class);
        assertThat(((CustomOAuth2User) restoredPrincipal).getUserId()).isEqualTo(42L);
        assertThat(((CustomOAuth2User) restoredPrincipal).getAttributes()).isEqualTo(attributes);
    }

    @Test
    @DisplayName("CustomOidcUser(구글)를 담은 SecurityContext는 직렬화된다")
    void customOidcUserIsSerializable() {
        OidcIdToken idToken = new OidcIdToken(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", "google-sub-1", "email", "test@gmail.com"));
        CustomOidcUser principal = new CustomOidcUser(7L, idToken, new OidcUserInfo(idToken.getClaims()));
        SecurityContextImpl context = new SecurityContextImpl(
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));

        assertThatCode(() -> roundTrip(context)).doesNotThrowAnyException();
    }

    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }
}
