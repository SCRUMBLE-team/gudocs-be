package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.dto.response.PushRegistrationResponse;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PushRegistrationService {

    private final PushRegistrationRepository pushRegistrationRepository;
    private final UserRepository userRepository;

    /**
     * FID 등록(upsert). 동일 fid가 있으면 새 행을 만들지 않고 소유자/상태를 갱신한다.
     * 다른 사용자가 등록한 fid면 현재 사용자로 연결을 변경한다.
     */
    @Transactional
    public PushRegistrationResponse register(Long userId, PushRegistrationRequest request) {
        User user = findUser(userId);

        PushRegistration registration = pushRegistrationRepository.findByFid(request.fid())
                .map(existing -> {
                    existing.reassignTo(user, request.platformOrDefault(), request.deviceName());
                    return existing;
                })
                .orElseGet(() -> PushRegistration.builder()
                        .user(user)
                        .fid(request.fid())
                        .platform(request.platformOrDefault())
                        .deviceName(request.deviceName())
                        .enabled(true)
                        .lastRegisteredAt(LocalDateTime.now())
                        .build());

        try {
            PushRegistration saved = pushRegistrationRepository.saveAndFlush(registration);
            return PushRegistrationResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 경합: UNIQUE(fid) 충돌 → 이미 만들어진 행을 다시 읽어 갱신
            PushRegistration existing = pushRegistrationRepository.findByFid(request.fid())
                    .orElseThrow(() -> e);
            existing.reassignTo(user, request.platformOrDefault(), request.deviceName());
            return PushRegistrationResponse.from(pushRegistrationRepository.save(existing));
        }
    }

    /**
     * 등록 해제(멱등). 실제 삭제 대신 enabled=false. 이미 비활성이어도 성공.
     */
    @Transactional
    public void unregister(Long userId, Long registrationId) {
        PushRegistration registration = pushRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUSH_REGISTRATION_NOT_FOUND));

        if (!registration.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PUSH_REGISTRATION_FORBIDDEN);
        }

        registration.disable();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
