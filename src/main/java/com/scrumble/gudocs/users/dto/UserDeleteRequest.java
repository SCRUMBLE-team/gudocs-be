package com.scrumble.gudocs.users.dto;

import jakarta.validation.constraints.NotBlank;

public record UserDeleteRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword
) {
}
