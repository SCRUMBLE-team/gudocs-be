package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.dto.response.PushRegistrationResponse;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushRegistrationService {

    private final PushRegistrationRepository pushRegistrationRepository;
    private final PushRegistrationWriter writer;

    /**
     * FID 등록(upsert). 동일 fid가 있으면 새 행을 만들지 않고 소유자/상태를 갱신한다.
     * 다른 사용자가 등록한 fid면 현재 사용자로 연결을 변경한다.
     * <p>
     * 동시 최초 등록 경합은 UNIQUE(fid)로 막고, 충돌 시 별도 트랜잭션({@link PushRegistrationWriter})으로
     * 복구해 500 없이 행 하나만 유지한다. (실패한 INSERT 트랜잭션에서 복구를 이어가지 않는다.)
     */
    public PushRegistrationResponse register(Long userId, PushRegistrationRequest request) {
        try {
            return PushRegistrationResponse.from(writer.createOrReassign(userId, request));
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 먼저 INSERT함 → 새 트랜잭션에서 기존 행을 갱신
            return PushRegistrationResponse.from(writer.reassignExisting(userId, request));
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
}
