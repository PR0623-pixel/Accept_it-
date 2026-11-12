package com.debate.game.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 문서의 메타데이터. 실제 HTTP 경로와 구현은 각 Controller를 따른다. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "AcceptIt Backend REST API",
        version = "v1",
        description = "토론 게임의 방 생성/입장, 역할 선택, 게임 시작, 발언 및 종료 REST API. "
                + "현재 토론은 PRO/CON 순서제이며 WebSocket 메시지는 포함하지 않습니다."))
public class OpenApiConfig {
}
