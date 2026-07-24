package com.scrumble.gudocs.users.dto;

import com.scrumble.gudocs.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserInfoResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "이름", example = "이성아")
        String name,

        @Schema(description = "이메일", example = "test@example.com")
        String email
) {

    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(user.getId(), user.getName(), user.getEmail());
    }
}