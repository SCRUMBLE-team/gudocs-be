package com.scrumble.gudocs.notification.dto.response;

import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PushRegistrationResponse(
        @Schema(description = "푸시 등록 ID", example = "1")
        Long registrationId,

        @Schema(description = "플랫폼", example = "WEB")
        PushPlatform platform,

        @Schema(description = "활성 여부", example = "true")
        boolean enabled,

        @Schema(description = "최종 수정 시각", example = "2026-07-30T09:00:00")
        LocalDateTime updatedAt
) {
    public static PushRegistrationResponse from(PushRegistration registration) {
        return new PushRegistrationResponse(
                registration.getId(),
                registration.getPlatform(),
                registration.isEnabled(),
                registration.getUpdatedAt());
    }
}
