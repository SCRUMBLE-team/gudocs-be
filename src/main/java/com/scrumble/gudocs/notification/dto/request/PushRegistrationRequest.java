package com.scrumble.gudocs.notification.dto.request;

import com.scrumble.gudocs.notification.entity.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PushRegistrationRequest(
        @Schema(description = "Firebase 설치/토큰 식별자", example = "firebase-installation-id")
        @NotBlank(message = "fid는 필수입니다.")
        String fid,

        @Schema(description = "플랫폼 (현재 WEB만 지원)", example = "WEB")
        PushPlatform platform,

        @Schema(description = "기기 이름", example = "Chrome on macOS")
        String deviceName
) {
    public PushPlatform platformOrDefault() {
        return platform != null ? platform : PushPlatform.WEB;
    }
}
