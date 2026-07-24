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

    // provider+providerId로 식별하므로 이메일은 unique 아님
    // (다른 소셜 제공자가 같은 이메일이면 별도 회원)
    @Column(nullable = false)
    private String email;

    public void updateName(String name) {
        this.name = name;
    }
}
