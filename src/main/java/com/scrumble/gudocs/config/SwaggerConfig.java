package com.scrumble.gudocs.config;

import com.scrumble.gudocs.global.security.CurrentUserId;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    static {
        // @CurrentUserId는 세션에서 주입되는 내부 값이므로 요청 파라미터로 노출하지 않는다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme cookieAuth = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("JSESSIONID")
                .description("""
                        JSESSIONID 기반 세션 쿠키 인증입니다.

                        - Swagger의 Authorize 버튼은 소셜 로그인을 수행하지 않습니다. \
                        (JSESSIONID는 HttpOnly라 값을 직접 넣을 수 없습니다.)
                        - 먼저 동일한 브라우저에서 `/oauth2/authorization/{provider}`로 소셜 로그인해야 합니다.
                        - 로그인 후에는 브라우저가 보유한 세션 쿠키가 자동으로 실려 Swagger에서 인증 API를 호출할 수 있습니다.
                        """);

        return new OpenAPI()
                .info(new Info()
                        .title("Gudocs API")
                        .description("구독 서비스 통합 관리 대시보드 API 명세서")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", cookieAuth));
    }
}