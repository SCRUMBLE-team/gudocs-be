package com.scrumble.gudocs.global.security;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현재 로그인한 사용자의 user.id를 컨트롤러 파라미터로 주입한다.
 * 세션에서 주입되는 내부 값이므로 Swagger 요청 파라미터로 노출하지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface CurrentUserId {
}
