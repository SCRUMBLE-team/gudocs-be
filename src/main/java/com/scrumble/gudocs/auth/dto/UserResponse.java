package com.scrumble.gudocs.auth.dto;

import com.scrumble.gudocs.users.entity.User;

public record UserResponse(Long id, String name, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
