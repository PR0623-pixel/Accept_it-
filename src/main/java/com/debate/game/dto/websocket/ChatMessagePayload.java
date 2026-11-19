package com.debate.game.dto.websocket;

/**
 * 프론트 frontend/src/types.ts의 ChatMessage에 대응한다.
 * SYSTEM 메시지는 senderId, nickname, stance, keyword가 null일 수 있다.
 */
public record ChatMessagePayload(
        String id,
        String kind,
        String senderId,
        String nickname,
        String stance,
        String text,
        String keyword,
        long createdAt
) {
}