package com.babylion.team1.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    /** Swagger UI의 Authorize 버튼에 액세스 토큰을 넣으면 모든 요청에 Authorization: Bearer 헤더가 붙는다. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("1팀 API")
                        .description("아기사자 프로젝트 1팀 명세서")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인 응답의 accessToken을 입력하세요. (Bearer 접두사 없이)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
