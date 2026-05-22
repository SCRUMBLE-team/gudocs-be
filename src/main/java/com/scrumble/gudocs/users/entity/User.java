package com.scrumble.gudocs.users.entity;

import com.scrumble.gudocs.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    public void updateName(String name) {
        this.name = name;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
