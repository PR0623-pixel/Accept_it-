package com.debate.game.websocket;

import com.debate.game.dto.websocket.InitialSyncPayload;

import java.util.Optional;

/**
 * Room 구현체와 WebSocket 핸들러 사이의 조회 경계.
 *
 * 구현체는 roomCode와 playerId의 유효성을 확인하고,
 * 프론트 전송용 InitialSyncPayload를 반환한다.
 */
public interface InitialSyncProvider {

    Optional<InitialSyncPayload> load(
            String roomCode,
            String playerId
    );
}