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

    // 소셜 로그인 후 온보딩에서 입력받는다 (신규 유저는 최초 null)
    @Column
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    public void updateName(String name) {
        this.name = name;
    }
}
