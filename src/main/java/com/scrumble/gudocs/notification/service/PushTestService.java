package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.dto.response.PushTestResponse;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 테스트 푸시 발송(진단용). 현재 사용자의 활성 기기에 즉시 푸시를 쏴서
 * setFid 발송이 실기기에 실제 도달하는지 확인한다. 스케줄러/중복이력 로직을 거치지 않고
 * PushSender만 직접 호출한다.
 */
@Service
@RequiredArgsConstructor
public class PushTestService {

    private final PushRegistrationRepository pushRegistrationRepository;
    private final PushSender pushSender;

    public PushTestResponse sendTest(Long userId) {
        List<PushRegistration> registrations = pushRegistrationRepository.findByUserIdAndEnabledTrue(userId);
        PushMessage message = new PushMessage(
                "구독 알림 테스트",
                "이 알림이 보이면 푸시 발송이 정상 동작하는 거예요.",
                Map.of("type", "TEST"));

        List<PushTestResponse.DeviceResult> results = registrations.stream()
                .map(r -> new PushTestResponse.DeviceResult(
                        r.getId(), mask(r.getFid()), pushSender.send(r.getFid(), message).name()))
                .toList();

        return new PushTestResponse(
                pushSender.getClass().getSimpleName(), results.size(), results);
    }

    private String mask(String fid) {
        if (fid == null || fid.length() <= 6) {
            return "***";
        }
        return fid.substring(0, 6) + "***";
    }
}
