package com.scrumble.gudocs.notification.dto.request;

import com.scrumble.gudocs.notification.entity.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushRegistrationRequest(
        @Schema(description = "Firebase Installation ID (FID)", example = "firebase-installation-id")
        @NotBlank(message = "fid는 필수입니다.")
        @Size(max = 255, message = "fid는 255자 이하여야 합니다.")
        String fid,

        @Schema(description = "플랫폼 (현재 WEB만 지원)", example = "WEB")
        PushPlatform platform,

        @Schema(description = "기기 이름", example = "Chrome on macOS")
        @Size(max = 255, message = "deviceName은 255자 이하여야 합니다.")
        String deviceName
) {
    public PushPlatform platformOrDefault() {
        return platform != null ? platform : PushPlatform.WEB;
    }
}
