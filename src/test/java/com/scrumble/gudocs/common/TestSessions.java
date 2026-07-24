package com.scrumble.gudocs.common;

import com.scrumble.gudocs.auth.oauth.CustomOAuth2User;
import com.scrumble.gudocs.users.entity.SocialAccount;
import com.scrumble.gudocs.users.entity.SocialProvider;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 소셜 로그인 세션을 흉내내는 테스트 헬퍼.
 * 실제 OAuth2 흐름 대신 user + social_account를 만들고,
 * CustomOAuth2User principal이 담긴 SecurityContext를 세션에 심어준다.
 */
public final class TestSessions {

    private static final AtomicLong SEQ = new AtomicLong();

    private TestSessions() {
    }

    public static User createUser(UserRepository userRepository,
                                  SocialAccountRepository socialAccountRepository,
                                  String name, String email) {
        User user = userRepository.save(User.builder().name(name).email(email).build());
        socialAccountRepository.save(SocialAccount.builder()
                .user(user)
                .provider(SocialProvider.GOOGLE)
                .providerId("google-" + SEQ.incrementAndGet())
                .email(email)
                .emailVerified(true)
                .build());
        return user;
    }

    public static MockHttpSession authenticate(User user) {
        CustomOAuth2User principal =
                new CustomOAuth2User(user.getId(), Map.of("sub", String.valueOf(user.getId())));
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "google");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    public static MockHttpSession loginNew(UserRepository userRepository,
                                           SocialAccountRepository socialAccountRepository,
                                           String name, String email) {
        return authenticate(createUser(userRepository, socialAccountRepository, name, email));
    }
}
