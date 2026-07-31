package com.scrumble.gudocs.notification.push;

/**
 * 단일 기기(fid) 발송 결과.
 * INVALID_TOKEN: 더 이상 유효하지 않은 토큰 → 등록 비활성화 대상.
 * FAILED: 일시적 실패 → 등록 유지, 다음 기회 재시도.
 */
public enum PushResult {
    SUCCESS,
    INVALID_TOKEN,
    FAILED
}
