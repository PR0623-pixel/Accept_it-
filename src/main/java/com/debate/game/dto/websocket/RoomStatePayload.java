package com.debate.game.dto.websocket;

import java.util.List;

/**
 * 프론트 frontend/src/types.ts의 RoomState에 대응하는 전송 DTO.
 * 데이터베이스 엔티티와 분리해 Room 모델 변경의 영향을 제한한다.
 */
public record RoomStatePayload(
        String roomCode,
        long serverTime,
        String phase,
        List<PlayerPayload> players,
        RoomSettingsPayload settings,
        ItemPayload currentItem,
        Long itemEndsAt,
        SpeechPayload speech,
        String lastSpeakerId,
        PendingRebuttalPayload pendingRebuttal,
        EffectPayload effect,
        List<ItemResultPayload> results
) {
    public record PlayerPayload(
            String id,
            String nickname,
            String role,
            boolean ready,
            String stance,
            boolean hasVoted,
            boolean isHost
    ) {
    }

    public record RoomSettingsPayload(
            int maxItems,
            int itemSeconds,
            int cardsPerItem
    ) {
    }

    public record ItemPayload(
            int index,
            String name,
            String description
    ) {
    }

    public record SpeechPayload(
            String speakerId,
            String kind,
            long endsAt,
            String keyword
    ) {
    }

    public record PendingRebuttalPayload(
            String requesterId,
            String targetId,
            String keyword
    ) {
    }

    public record EffectPayload(
            long id,
            String type,
            String targetId
    ) {
    }

    public record ItemResultPayload(
            String itemName,
            boolean accepted
    ) {
    }
}