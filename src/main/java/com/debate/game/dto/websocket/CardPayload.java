package com.debate.game.dto.websocket;

/**
 * 프론트 frontend/src/types.ts의 Card에 대응한다.
 * HAND 이벤트는 접속한 본인에게만 전달해야 한다.
 */
public record CardPayload(
        long cardId,
        String keyword,
        String title,
        String description,
        boolean used
) {
}