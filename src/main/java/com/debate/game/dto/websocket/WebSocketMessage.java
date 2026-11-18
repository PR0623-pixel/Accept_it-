package com.debate.game.dto.websocket;

/**
 * 프론트엔드와 주고받는 WebSocket 메시지 봉투.
 * JSON 형식: {"type":"ROOM_STATE","payload":{...}}
 */
public record WebSocketMessage<T>(
        String type,
        T payload
) {
}