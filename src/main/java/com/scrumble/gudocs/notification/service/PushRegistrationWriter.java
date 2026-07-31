package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 푸시 등록 쓰기 작업을 각각 독립 트랜잭션으로 수행한다.
 * <p>
 * UNIQUE(fid) 경합 시, 실패한 INSERT 트랜잭션은 rollback-only가 되므로 같은 트랜잭션에서
 * 복구를 시도하면 안 된다. 그래서 최초 시도({@link #createOrReassign})와 경합 복구
 * ({@link #reassignExisting})를 별도 메서드로 분리한다. 호출자({@link PushRegistrationService#register})는
 * <b>트랜잭션 없이</b> 호출하므로 각 메서드는 자기 트랜잭션을 새로 열고, 첫 시도에서
 * {@code DataIntegrityViolationException}이 나면(=이미 롤백된 별도 트랜잭션) 새 트랜잭션인
 * 복구 메서드를 호출한다. (호출자가 트랜잭션을 갖지 않아야 하므로 register에 @Transactional을 두지 않는다.)
 */
@Component
@RequiredArgsConstructor
public class PushRegistrationWriter {

    private final PushRegistrationRepository pushRegistrationRepository;
    private final UserRepository userRepository;

    /**
     * 기존 fid가 있으면 소유자/상태를 갱신하고, 없으면 새로 INSERT한다.
     * 동시 최초 등록 경합 시 이 메서드에서 {@code DataIntegrityViolationException}이 발생할 수 있다.
     */
    @Transactional
    public PushRegistration createOrReassign(Long userId, PushRegistrationRequest request) {
        User user = findUser(userId);
        return pushRegistrationRepository.findByFid(request.fid())
                .map(existing -> {
                    existing.reassignTo(user, request.platformOrDefault(), request.deviceName());
                    return existing;
                })
                .orElseGet(() -> pushRegistrationRepository.saveAndFlush(PushRegistration.builder()
                        .user(user)
                        .fid(request.fid())
                        .platform(request.platformOrDefault())
                        .deviceName(request.deviceName())
                        .enabled(true)
                        .lastRegisteredAt(LocalDateTime.now())
                        .build()));
    }

    /**
     * 경합 복구용: 이미 커밋된 행을 새 트랜잭션에서 다시 읽어 현재 사용자로 갱신한다.
     */
    @Transactional
    public PushRegistration reassignExisting(Long userId, PushRegistrationRequest request) {
        User user = findUser(userId);
        PushRegistration existing = pushRegistrationRepository.findByFid(request.fid())
                .orElseThrow(() -> new BusinessException(ErrorCode.PUSH_REGISTRATION_NOT_FOUND));
        existing.reassignTo(user, request.platformOrDefault(), request.deviceName());
        return existing;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
