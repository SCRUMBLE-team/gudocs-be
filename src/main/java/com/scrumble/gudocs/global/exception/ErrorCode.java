package com.scrumble.gudocs.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 서비스를 찾을 수 없습니다."),
    SUBSCRIPTION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 구독 서비스에 접근할 권한이 없습니다."),
    INVALID_BILLING_MONTH(HttpStatus.BAD_REQUEST, "연간 결제의 경우 결제 월(1~12)을 입력해야 합니다."),
    BILLING_MONTH_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "월간 결제의 경우 결제 월을 입력할 수 없습니다."),
    INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "year, month는 유효한 값이어야 하며 month는 1~12 사이여야 합니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");

    private final HttpStatus status;
    private final String message;
}