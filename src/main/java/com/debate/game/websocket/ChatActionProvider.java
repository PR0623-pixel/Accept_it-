package com.debate.game.websocket;

import com.debate.game.dto.websocket.ChatMessagePayload;

/**
 * SEND_CHAT의 서버 검증과 채팅 저장을 담당한다.
 *
 * 구현체는 다음을 검증해야 한다.
 * - roomCode와 playerId가 유효한 참가자인지
 * - 사회자인지 또는 현재 발언권자인지
 * - 현재 단계에서 채팅이 가능한지
 * - 텍스트 길이가 200자를 넘지 않는지
 *
 * 검증 성공 시 저장된 ChatMessagePayload를 반환한다.
 */
public interface ChatActionProvider {

    ChatMessagePayload sendChat(
            String roomCode,
            String playerId,
            String text
    );
}