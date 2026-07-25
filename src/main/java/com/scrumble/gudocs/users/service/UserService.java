package com.scrumble.gudocs.users.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.dto.UserInfoResponse;
import com.scrumble.gudocs.users.dto.UserNameUpdateRequest;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public UserInfoResponse updateName(Long userId, UserNameUpdateRequest request) {
        User user = findUser(userId);
        user.updateName(request.name());
        return UserInfoResponse.from(user);
    }

    @Transactional
    public void deleteAccount(Long userId) {
        User user = findUser(userId);
        subscriptionRepository.hardDeleteAllByUser(user);
        socialAccountRepository.deleteAllByUser(user);
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
