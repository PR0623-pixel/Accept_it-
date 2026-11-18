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
    public record LobbySettings(int maxItems, int itemSeconds, int cardsPerItem) {}
}
