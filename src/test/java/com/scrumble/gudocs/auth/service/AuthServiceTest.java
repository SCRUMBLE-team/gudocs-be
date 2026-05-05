package com.scrumble.gudocs.auth.service;

import com.scrumble.gudocs.auth.dto.SignupRequest;
import com.scrumble.gudocs.auth.dto.UserResponse;
import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void 회원가입_성공() {
        SignupRequest request = new SignupRequest("테스터", "test@example.com", "Password1!");
        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("Password1!")).willReturn("hashed");

        authService.signup(request);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void 회원가입_이메일_중복_실패() {
        SignupRequest request = new SignupRequest("테스터", "dup@example.com", "Password1!");
        given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void 현재_사용자_조회_성공() {
        User user = User.builder()
                .name("테스터")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        UserResponse response = authService.getCurrentUser("test@example.com");

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("테스터");
    }

    @Test
    void 현재_사용자_조회_없는_사용자() {
        given(userRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("notfound@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void loadUserByUsername_성공() {
        User user = User.builder()
                .name("테스터")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        var userDetails = authService.loadUserByUsername("test@example.com");

        assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
    }

    @Test
    void loadUserByUsername_없는_사용자() {
        given(userRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loadUserByUsername("notfound@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}