package com.scrumble.gudocs.notification.dto.response;

import java.util.List;

/**
 * 테스트 푸시 발송 결과. setFid 발송이 실기기에 실제 도달하는지 검증하는 진단용 응답.
 *
 * @param senderType 실제로 동작한 PushSender 구현 이름.
 *                   FcmPushSender면 실제 발송, NoopPushSender면 발송 없이 SUCCESS(검증 의미 없음).
 * @param deviceCount 발송을 시도한 활성 기기 수
 * @param results     기기별 발송 결과
 */
public record PushTestResponse(String senderType, int deviceCount, List<DeviceResult> results) {

    /**
     * @param registrationId 등록 id
     * @param fid            마스킹된 fid (앞 6자리만)
     * @param result         SUCCESS / INVALID_TOKEN / FAILED
     */
    public record DeviceResult(Long registrationId, String fid, String result) {
    }
}
