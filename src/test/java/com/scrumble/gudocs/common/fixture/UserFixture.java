package com.scrumble.gudocs.common.fixture;

import com.scrumble.gudocs.users.entity.User;

public class UserFixture {

    public static User create() {
        return User.builder()
                .name("테스터")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();
    }

    public static User create(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .passwordHash("hashed")
                .build();
    }
}
