package com.debate.game.dto.websocket;

import java.util.List;

/**
 * WebSocket 연결 직후 전송할 초기 동기화 데이터.
 * Room/JPA 엔티티를 노출하지 않는 전송 계약이다.
 */
public record InitialSyncPayload(
        RoomStatePayload roomState,
        List<CardPayload> hand,
        List<ChatMessagePayload> chatHistory
) {
    public InitialSyncPayload {
        hand = List.copyOf(hand);
        chatHistory = List.copyOf(chatHistory);
    }
}