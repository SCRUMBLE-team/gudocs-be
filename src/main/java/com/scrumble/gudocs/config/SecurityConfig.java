package com.scrumble.gudocs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.auth.oauth.CustomOAuth2UserService;
import com.scrumble.gudocs.auth.oauth.CustomOidcUserService;
import com.scrumble.gudocs.auth.oauth.OAuth2LoginFailureHandler;
import com.scrumble.gudocs.auth.oauth.UserPrincipal;
import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.users.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
            ObjectMapper objectMapper,
            UserRepository userRepository) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
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
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(response.getWriter(),
                                    ApiResponse.error("로그인이 필요합니다."));
                        })
                );
        return http.build();
    }
}
