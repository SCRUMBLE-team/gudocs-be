package com.scrumble.gudocs.config;

import com.scrumble.gudocs.auth.oauth.CustomOAuth2UserService;
import com.scrumble.gudocs.auth.oauth.CustomOidcUserService;
import com.scrumble.gudocs.auth.oauth.UserPrincipal;
import com.scrumble.gudocs.users.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.oauth.success-redirect}")
    private String oauthSuccessRedirect;

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService,
            UserRepository userRepository) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                        .successHandler((request, response, authentication) -> {
                            // 이름 미설정(신규 가입) → 온보딩(이름 입력) 화면으로, 아니면 정상 진입
                            Long userId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
                            boolean needsOnboarding = userRepository.findById(userId)
                                    .map(u -> u.getName() == null || u.getName().isBlank())
                                    .orElse(false);
                            response.sendRedirect(needsOnboarding
                                    ? oauthSuccessRedirect + "?onboarding=1"
                                    : oauthSuccessRedirect);
                        })
                        .failureHandler((request, response, exception) -> {
                            String reason = URLEncoder.encode(
                                    exception.getMessage() == null ? "" : exception.getMessage(),
                                    StandardCharsets.UTF_8);
                            response.sendRedirect(oauthSuccessRedirect + "?login=fail&reason=" + reason);
                        })
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                        "Unauthorized"))
                );
        return http.build();
    }
}
