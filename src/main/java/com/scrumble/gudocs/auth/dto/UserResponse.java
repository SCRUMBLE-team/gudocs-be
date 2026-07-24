package com.scrumble.gudocs.auth.dto;

import com.scrumble.gudocs.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long id,

        @Schema(description = "이름", example = "이성아")
        String name,

        @Schema(description = "이메일", example = "test@example.com")
        String email
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}