package com.debate.game.dto;

import java.util.List;

/** R06 read-only lobby projection; not the full WebSocket ROOM_STATE payload. */
public record LobbySnapshotResponse(
        String roomCode,
        String hostPlayerId,
        String phase,
        List<LobbyPlayer> players,
        LobbySettings settings
) {
    public record LobbyPlayer(String id, String nickname, String role, boolean ready, boolean isHost) {}
    // The frontend currently consumes the first three keys. The extra two are
    // persisted backend settings until the frontend adds editable controls.
    public record LobbySettings(int maxItems, int itemSeconds, int cardsPerItem,
                                int speechSeconds, int rebuttalSeconds) {}
}
