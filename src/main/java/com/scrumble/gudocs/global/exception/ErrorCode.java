package com.scrumble.gudocs.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 서비스를 찾을 수 없습니다."),
    SUBSCRIPTION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 구독 서비스에 접근할 권한이 없습니다."),
    INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "year, month는 유효한 값이어야 하며 month는 1~12 사이여야 합니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다. (jpg, png, 최대 10MB)"),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "OCR 처리 중 오류가 발생했습니다."),
    PUSH_REGISTRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "푸시 등록 정보를 찾을 수 없습니다."),
    PUSH_REGISTRATION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 푸시 등록 정보에 접근할 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;
}