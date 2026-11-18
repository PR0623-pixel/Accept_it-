package com.debate.game.websocket;

import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.ChatMessagePayload;
import com.debate.game.dto.websocket.RoomStatePayload;

import java.util.List;
import java.util.Optional;

/**
 * WebSocket 초기 동기화에 필요한 데이터를 제공하는 경계.
 * 구현체만 Room/Player/Card 엔티티 및 저장소에 의존하도록 한다.
 */
public interface RoomSnapshotProvider {

    Optional<RoomStatePayload> findRoomState(String roomCode, String playerId);

    List<CardPayload> findHand(String roomCode, String playerId);

    List<ChatMessagePayload> findChatHistory(String roomCode, String playerId);
}