package com.scrumble.gudocs.auth.oauth;

/**
 * 로그인 수단(OAuth2/OIDC)과 무관하게 user.id를 노출하는 세션 principal 공통 계약.
 */
public interface UserPrincipal {
    Long getUserId();
}
