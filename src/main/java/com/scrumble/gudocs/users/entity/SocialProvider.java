package com.scrumble.gudocs.users.entity;

import java.util.Arrays;

public enum SocialProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    public static SocialProvider from(String registrationId) {
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(registrationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 소셜 provider: " + registrationId));
    }
}
