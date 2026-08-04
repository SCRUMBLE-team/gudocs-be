package com.scrumble.gudocs.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 로그인 실패 시 프론트로 리다이렉트한다.
 * 예외 세부 내용은 URL에 노출하지 않고 고정 코드만 전달하며, 실제 원인은 서버 로그에 남긴다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);
    private static final String FAILURE_QUERY = "?login=fail&code=OAUTH_LOGIN_FAILED";

    private final String successRedirect;

    public OAuth2LoginFailureHandler(@Value("${app.oauth.success-redirect}") String successRedirect) {
        this.successRedirect = successRedirect;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("OAuth2 로그인 실패", exception);
        response.sendRedirect(successRedirect + FAILURE_QUERY);
    }
}