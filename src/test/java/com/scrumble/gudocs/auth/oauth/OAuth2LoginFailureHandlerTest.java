package com.scrumble.gudocs.auth.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginFailureHandlerTest {

    private static final String FRONT = "https://gudocs-fe-v2.vercel.app";

    @Test
    void 실패_리다이렉트에_예외_메시지가_노출되지_않고_고정_코드만_포함() throws Exception {
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(FRONT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception =
                new TestAuthenticationException("민감한_예외_상세_정보_secret_detail");

        handler.onAuthenticationFailure(request, response, exception);

        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).isEqualTo(FRONT + "?login=fail&code=OAUTH_LOGIN_FAILED");
        assertThat(redirectedUrl).doesNotContain("secret_detail");
        assertThat(redirectedUrl).doesNotContain("reason");
    }

    private static class TestAuthenticationException extends AuthenticationException {
        TestAuthenticationException(String msg) {
            super(msg);
        }
    }
}
