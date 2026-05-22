package com.scrumble.gudocs.users.dto;

import jakarta.validation.constraints.NotBlank;

public record UserNameUpdateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
}
