package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialProvider;

import java.util.Map;

/**
 * provider별로 제각각인 userinfo 응답을 공통 형태로 정규화한다.
 * 새 provider 추가 시 switch 분기와 of* 메서드만 추가하면 된다.
 */
public record OAuth2UserInfo(
        SocialProvider provider,
        String providerId,
        String email,
        boolean emailVerified,
        String name
) {

    public static OAuth2UserInfo of(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> ofGoogle(attributes);
            case KAKAO -> ofKakao(attributes);
            case NAVER -> ofNaver(attributes);
        };
    }

    // Google: 평면 구조 (OIDC userinfo)
    private static OAuth2UserInfo ofGoogle(Map<String, Object> attr) {
        return new OAuth2UserInfo(
                SocialProvider.GOOGLE,
                str(attr.get("sub")),
                str(attr.get("email")),
                bool(attr.get("email_verified")),
                str(attr.get("name"))
        );
    }

    // Kakao: id + kakao_account { email, is_email_verified, profile { nickname } }
    private static OAuth2UserInfo ofKakao(Map<String, Object> attr) {
        Map<String, Object> account = asMap(attr.get("kakao_account"));
        Map<String, Object> profile = asMap(account.get("profile"));
        return new OAuth2UserInfo(
                SocialProvider.KAKAO,
                str(attr.get("id")),
                str(account.get("email")),
                bool(account.get("is_email_verified")),
                str(profile.get("nickname"))
        );
    }

    // Naver: response { id, email, name }
    private static OAuth2UserInfo ofNaver(Map<String, Object> attr) {
        Map<String, Object> response = asMap(attr.get("response"));
        return new OAuth2UserInfo(
                SocialProvider.NAVER,
                str(response.get("id")),
                str(response.get("email")),
                true, // 네이버는 인증된 이메일만 제공
                str(response.get("name"))
        );
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b && b;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }
}
