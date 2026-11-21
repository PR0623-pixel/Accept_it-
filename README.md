## 개요

`feat/game-flow-stabilization` 브랜치에서 S01~S05 단계의 게임 진행·종료 흐름을 안정화하고, Vite 8과 React 플러그인의 버전 불일치를 수정했습니다.

토론 종료, 서버 타이머, 카드 소진, 발언/반론 상태와 DB 정합성, 물품 판정 및 최종 게임 종료를 일관된 WebSocket 흐름으로 처리하도록 보강했습니다.

- **Base:** `main`
- **Compare:** `feat/game-flow-stabilization`
- **DB 스키마 변경:** 없음 (신규 Flyway 마이그레이션 없음)

## 주요 변경 사항

### S01. 토론 종료 상태 전환 공통화

- `RoomGameState.finishDebateIfActive()`로 `DEBATE → MODERATOR_DECISION` 전환 로직을 공통화했습니다.
- 수동 종료와 자동 종료의 중복 상태 전환 및 중복 WebSocket 전송을 방지했습니다.
- 토론 종료 시 진행 중 발언·반론 정보를 정리하고, 미처리 반론의 예약 카드 반환 경로를 마련했습니다.
- 종료 후 `ROOM_STATE` 및 참가자별 `HAND`를 갱신하도록 했습니다.

### S02. 서버 기준 타이머 구현

- `@EnableScheduling`과 `GameTimerService`를 이용한 주기적 타이머 검사(기본 250ms)를 추가했습니다.
- 물품 전체 토론 시간과 일반 발언·승인된 반론의 제한 시간을 서버 기준으로 검증합니다.
- 시간 만료 시 발언권 종료 또는 `MODERATOR_DECISION` 전환을 자동 처리합니다.
- DB에 설정된 `speechSeconds`, `rebuttalSeconds`를 실제 게임 상태에 반영하고, 만료된 요청의 카드 사용 처리를 방지합니다.

### S03. 카드 소진 시 토론 자동 종료

- 현재 물품의 PRO/CON 중 한쪽이라도 사용 가능한 카드가 소진되면 토론 종료 조건을 충족하도록 했습니다.
- 진행 중인 발언·승인된 반론 및 심사 대기 중인 반론을 마무리한 뒤 종료하며, 물품 전체 제한 시간 만료를 우선합니다.
- 반론 묵살 시 반환된 카드를 반영하고, 다음 물품의 카드와 이전 물품의 카드를 분리해 처리합니다.

### S04. 카드·발언·반론 정합성 보강

- `START_SPEECH`, `REQUEST_REBUTTAL` 요청의 권한·현재 단계·카드 소유·사용 여부 등을 DB 갱신 전에 검증합니다.
- 카드 사용 및 복구 시 방·현재 물품·진영 조건을 확인하고, 중복 요청에 따른 카드 이중 사용을 방지합니다.
- 반론 묵살 및 미처리 반론 종료 시 DB의 `is_used`와 개인별 `HAND.used` 상태를 맞추도록 보강했습니다.
- 카드 부족 또는 중복 배분 실패 시 마지막 투표를 되돌려 재시도할 수 있도록 처리했습니다.
- 같은 방의 관련 요청을 직렬화해 동시성 문제를 줄였습니다.

### S05. 물품 판정 및 게임 종료 DB 정합성

- 사회자의 `DECIDE_ITEM` 처리에 대해 `RoomService.commitItemProgress()` 단일 트랜잭션 경로를 도입했습니다.
- 중간 물품 판정 시 `IN_PROGRESS`와 다음 물품의 `VOTING`을 유지하고, 마지막 물품 판정 시 DB `FINISHED` 및 게임 `RESULT`로 전환합니다.
- 현재 물품 ID를 검증하고, DB 커밋 성공 이후에만 메모리의 상태와 결과를 변경하도록 했습니다.
- `RESULT` 진입 시 `ROOM_STATE`, 개인별 빈 `HAND`, `CHAT_HISTORY`를 동기화하며 종료 후 추가 명령을 거부합니다.
- DB만 종료시키던 구형 `POST /api/debate/{roomId}/finish` 우회 경로는 `409 Conflict`로 차단했습니다.

### 프론트엔드 호환성 수정

- Vite 8과 호환되도록 `@vitejs/plugin-react` 의존성을 6.x 계열로 갱신했습니다.
- `frontend/package.json`, `frontend/package-lock.json`을 정리하고, 의존성 설치·재설치·빌드 확인용 `frontend/fix-vite-react-deps.cmd`를 추가했습니다.
- 확인된 설치 버전: `vite@8.3.8`, `@vitejs/plugin-react@6.3.0`.
- 기존 Vite의 `/api`, `/ws` 개발 프록시 및 `VITE_USE_MOCK=false`를 통한 실서버 연결 방식을 유지합니다.

## 테스트 및 검증

- [x] **백엔드 자동 테스트 기록 확인:** 첨부 소스의 `build/test-results/test` XML 기준 **47개 테스트 클래스, 총 242개 테스트 / 실패 0 / 오류 0 / 건너뜀 0** (2026-11-21 기록). *이번 PR 문서 작성 과정에서 재실행한 결과는 아닙니다.*
- [x] **프론트엔드:** `npm install --save-dev "@vitejs/plugin-react@^6.0.3"` 성공
- [x] **프론트엔드:** `npm ci` 성공, 설치 감사 결과 취약점 0개
- [x] **프론트엔드:** `npm run build` 성공 (`tsc --noEmit && vite build`)
- [x] **의존성 확인:** `npm ls vite @vitejs/plugin-react --depth=0` 성공
- [ ] **수동 통합 검증:** 실제 Docker MySQL + Spring Boot + Vite 연결에서 사회자 1명/객원 2명의 전체 흐름 확인
- [ ] **DB 실측 검증:** 마지막 판정 후 `room.status=FINISHED`, 다음 물품 진행 시 `IN_PROGRESS` 및 `current_item_id` 갱신 확인

### 재현 명령어 (Windows PowerShell)

```powershell
# 프로젝트 루트에서
docker compose up -d mysql-db
.\gradlew.bat clean test
.\gradlew.bat bootRun

# 별도 터미널에서
cd frontend
npm ci
npm run build
npm run dev
```

실제 백엔드 연결 테스트 시 `frontend/.env.local`에 `VITE_USE_MOCK=false`를 지정합니다.

## 리뷰 시 확인 사항

- [ ] 토론 수동 종료·전체 시간 만료·카드 소진 종료가 동시에 발생해도 전환/전송이 중복되지 않는지 확인
- [ ] 반론 예약·묵살·승인·시간 만료에 대해 DB `is_used`와 개인별 `HAND.used`가 일치하는지 확인
- [ ] 최종 `DECIDE_ITEM` 중복 요청 및 DB 실패 시 결과가 중복 기록되거나 메모리 상태만 변경되지 않는지 확인
- [ ] **루트 `package-lock.json` 확인:** 프론트엔드용 lockfile 외에 루트에 빈 패키지 목록의 lockfile이 추가됨. 필요 없다면 제외 검토

## 알려진 제약 / 후속 작업

- 방 상태·물품 진행은 일부 DB에 반영되지만, **물품별 판정 결과 목록은 현재 메모리에만 보관**됩니다. 서버 재시작 후 게임 상태/타이머/결과 복구 및 완전한 영속화는 후속 작업 범위입니다.
- 인메모리 상태와 DB는 단일 ACID 트랜잭션이 아니므로, DB 커밋 직후 프로세스가 비정상 종료되는 극단적 상황에 대한 복구는 별도 보강이 필요합니다.
- WebSocket `playerId` 인증 강화와 정식 상황 카드 콘텐츠 등록도 별도 후속 작업입니다.
- 자동 테스트 통과와 별개로 3인 브라우저·실제 MySQL을 사용하는 종단 간 검증 완료 여부는 병합 전에 확인해야 합니다.
