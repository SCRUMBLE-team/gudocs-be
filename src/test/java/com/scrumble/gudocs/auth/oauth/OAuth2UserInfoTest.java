package com.scrumble.gudocs.auth.oauth;

import com.scrumble.gudocs.users.entity.SocialProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2UserInfoTest {

    @Test
    void 구글_userinfo_파싱() {
        Map<String, Object> attributes = Map.of(
                "sub", "google-123",
                "email", "user@gmail.com",
                "email_verified", true,
                "name", "구글유저"
        );

        OAuth2UserInfo info = OAuth2UserInfo.of(SocialProvider.GOOGLE, attributes);

        assertThat(info.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(info.providerId()).isEqualTo("google-123");
        assertThat(info.email()).isEqualTo("user@gmail.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.name()).isEqualTo("구글유저");
    }

    @Test
    void 카카오_userinfo_파싱_중첩구조() {
        Map<String, Object> attributes = Map.of(
                "id", 987654321L,
                "kakao_account", Map.of(
                        "email", "user@kakao.com",
                        "is_email_verified", true,
                        "profile", Map.of("nickname", "카카오유저")
                )
        );

        OAuth2UserInfo info = OAuth2UserInfo.of(SocialProvider.KAKAO, attributes);

        assertThat(info.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(info.providerId()).isEqualTo("987654321");
        assertThat(info.email()).isEqualTo("user@kakao.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.name()).isEqualTo("카카오유저");
    }

    @Test
    void 네이버_userinfo_파싱_response_래핑() {
        Map<String, Object> attributes = Map.of(
                "response", Map.of(
                        "id", "naver-abc",
                        "email", "user@naver.com",
                        "name", "네이버유저"
                )
        );

        OAuth2UserInfo info = OAuth2UserInfo.of(SocialProvider.NAVER, attributes);

        assertThat(info.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(info.providerId()).isEqualTo("naver-abc");
        assertThat(info.email()).isEqualTo("user@naver.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.name()).isEqualTo("네이버유저");
    }

    @Test
    void 카카오_이메일_동의_안한_경우_null() {
        Map<String, Object> attributes = Map.of(
                "id", 111L,
                "kakao_account", Map.of(
                        "profile", Map.of("nickname", "닉네임만")
                )
        );

        OAuth2UserInfo info = OAuth2UserInfo.of(SocialProvider.KAKAO, attributes);

        assertThat(info.email()).isNull();
        assertThat(info.emailVerified()).isFalse();
        assertThat(info.name()).isEqualTo("닉네임만");
    }
}
