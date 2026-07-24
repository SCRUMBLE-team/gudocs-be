package com.scrumble.gudocs.users.entity;

import java.util.Arrays;

public enum SocialProvider {
    GOOGLE("구글"),
    KAKAO("카카오"),
    NAVER("네이버");

    private final String displayName;

    SocialProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SocialProvider from(String registrationId) {
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(registrationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 소셜 provider: " + registrationId));
    }
}
