package com.scrumble.gudocs.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UserNameUpdateRequest(
        @Schema(description = "변경할 이름", example = "이성아")
        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
}
