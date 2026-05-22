package com.scrumble.gudocs.users.dto;

import com.scrumble.gudocs.users.entity.User;

public record UserInfoResponse(Long userId, String name, String email) {

    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(user.getId(), user.getName(), user.getEmail());
    }
}
