package com.scrumble.gudocs.users.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.dto.UserDeleteRequest;
import com.scrumble.gudocs.users.dto.UserInfoResponse;
import com.scrumble.gudocs.users.dto.UserNameUpdateRequest;
import com.scrumble.gudocs.users.dto.UserPasswordUpdateRequest;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(String email) {
        User user = findByEmail(email);
        return UserInfoResponse.from(user);
    }

    @Transactional
    public UserInfoResponse updateName(String email, UserNameUpdateRequest request) {
        User user = findByEmail(email);
        user.updateName(request.name());
        return UserInfoResponse.from(user);
    }

    @Transactional
    public void updatePassword(String email, UserPasswordUpdateRequest request) {
        User user = findByEmail(email);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }

        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void deleteAccount(String email, UserDeleteRequest request, HttpServletRequest httpRequest) {
        User user = findByEmail(email);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        subscriptionRepository.deleteAllByUser(user);
        userRepository.delete(user);

        var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
