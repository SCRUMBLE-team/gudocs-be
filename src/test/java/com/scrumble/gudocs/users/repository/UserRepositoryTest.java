package com.scrumble.gudocs.users.repository;

import com.scrumble.gudocs.users.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    private User savedUser() {
        User user = User.builder()
                .name("테스터")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();
        return em.persistAndFlush(user);
    }

    @Test
    void findByEmail_성공() {
        savedUser();

        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByEmail_없는_이메일() {
        Optional<User> result = userRepository.findByEmail("notfound@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByEmail_존재하는_이메일() {
        savedUser();

        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    void existsByEmail_없는_이메일() {
        assertThat(userRepository.existsByEmail("notfound@example.com")).isFalse();
    }
}
