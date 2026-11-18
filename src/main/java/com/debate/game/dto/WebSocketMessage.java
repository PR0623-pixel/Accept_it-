package com.debate.game.dto;

import java.util.Map;

/**
 * 프론트엔드와 주고받는 WebSocket 메시지 형식.
 *
 * 예시:
 * {"type":"VOTE","payload":{"stance":"PRO"}}
 * {"type":"ERROR","payload":{"code":"INVALID","message":"요청을 처리할 수 없습니다."}}
 */
public record WebSocketMessage(
        String type,
        Map<String, Object> payload
) {
}