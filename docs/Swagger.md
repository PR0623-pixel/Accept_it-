# AcceptIt — Swagger / OpenAPI 적용 안내

## 적용 범위

- 기반 ZIP: `Accept_it-(20261112-114159).zip` (2026-11-12 기준)
- Java 21 / Spring Boot 4.1.2 / Gradle
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0` 도입
- `OpenApiConfig.java`에 제목, 버전, 설명 설정
- 기존 REST Controller 3개 및 요청 DTO에 Swagger 설명 추가
- 기존 Service/Repository/Entity/Flyway 로직은 변경하지 않음

## 접속 주소

Spring Boot가 8080 포트에서 실행 중일 때:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml
- Actuator health: http://localhost:8080/actuator/health (OpenAPI 문서에서는 제외)

`springdoc.packages-to-scan`과 `paths-to-match`를 통해 기존 `/api/**` REST API만 문서화한다.

## 문서화된 REST API (8개)

| 그룹 | HTTP | 경로 | 설명 |
|---|---|---|---|
| Room | POST | `/api/rooms` | 방 생성, 6자리 코드 반환 |
| Room | POST | `/api/rooms/{roomCode}/join` | 플레이어 입장 및 ID 반환 (playerName 쿼리 파라미터) |
| Room | POST | `/api/rooms/ready` | PRO/CON/MODERATOR 역할 선택 및 준비 |
| Game | POST | `/api/game/{roomId}/start` | 준비 상태 확인 및 더미 카드 배포 |
| Debate | POST | `/api/debate/chain` | 카드 사용 및 발언, 더미 평가 응답 |
| Debate | POST | `/api/debate/turn` | 일반 발언 및 PRO↔CON 차례 전환 |
| Debate | GET | `/api/debate/{roomId}/state` | 현재 토론 상태 및 발언 기록 조회 |
| Debate | POST | `/api/debate/{roomId}/finish` | 사회자 토론 종료 |

오류 응답 스키마는 F08의 `ErrorResponse(code, errorMessage)`를 사용하며, 기존 정상 응답 형식은 바꾸지 않았다.

## 실행 방법 (Windows PowerShell)

1. Docker MySQL 실행: `docker compose up -d mysql-db`
2. 프로젝트에서 사용하는 DB 환경변수를 설정 (F06 방법 참조)
3. `./gradlew.bat clean test` 실행
4. `./gradlew.bat bootRun` 실행
5. 브라우저에서 `http://localhost:8080/swagger-ui.html` 확인
6. 새 PowerShell 창에서 `./scripts/verify-swagger.ps1` 실행

`verify-swagger.ps1`은 OpenAPI 3 문서, API 8개, Swagger UI 접근 여부를 확인한다.

## Swagger 비활성화

기본적으로 로컬 개발 편의를 위해 활성화된다. 서버 환경변수에 `SWAGGER_ENABLED=false`를 주면
Swagger UI와 `/v3/api-docs` 자동 생성 엔드포인트를 비활성화할 수 있다.
외부 서비스 배포 시에는 접근제어 정책도 별도로 설계해야 한다.

## 주의사항 / 현재 구현 범위

- Swagger **Try it out**은 실제 HTTP 요청을 전송하여 MySQL에 방·플레이어·로그 등을 저장할 수 있다.
- 현재 역할 구조는 `PRO`, `CON`, `MODERATOR`이다. 새로운 게임 기획의 객원/사회자 및 라운드별 찬반 선택은 후속 작업이다.
- `GameStartService`의 카드 배포는 더미 데이터이며, `DebateService`의 체인 평가도 더미 점수/반응이다.
- `Room.turnTimeLimit`은 저장 및 응답에 사용되지만 서버 측 시간 경과 제한은 아직 구현되지 않았다.
- 실시간 WebSocket 메시지는 OpenAPI REST 문서에 자동으로 포함되지 않는다.
- `V2__add_debate_turn_state.sql`은 기반 ZIP에 이미 포함되어 있으며 이번 Swagger 작업에서 변경하지 않았다.
- 실제 Gradle 빌드·서버 실행·HTTP 검증 통과 여부는 각 개발 환경에서 확인하고 기록해야 한다.

## 검증 체크리스트

- [ ] `./gradlew.bat clean test` 성공
- [ ] MySQL 연결 및 `bootRun` 성공
- [ ] `/v3/api-docs`에서 OpenAPI JSON 확인
- [ ] `/swagger-ui.html`에서 Room / Game / Debate 8개 엔드포인트 확인
- [ ] `./scripts/verify-swagger.ps1` 통과
- [ ] Git 변경 내용 검토 및 커밋
