package com.debate.game.dto;

import java.util.List;

/** R06 read-only lobby projection; not the full WebSocket ROOM_STATE payload. */
public record LobbySnapshotResponse(
        String roomCode,
        String hostPlayerId,
        String phase,
        List<LobbyPlayer> players,
        LobbySettings settings,
        ItemSnapshot currentItem
) {
    public LobbySnapshotResponse(
            String roomCode,
            String hostPlayerId,
            String phase,
            List<LobbyPlayer> players,
            LobbySettings settings
    ) {
        this(roomCode, hostPlayerId, phase, players, settings, null);
    }

    public record LobbyPlayer(
            String id, String nickname, String role, boolean ready, boolean isHost
    ) {}

    public record LobbySettings(
            int maxItems, int itemSeconds, int cardsPerItem,
            int speechSeconds, int rebuttalSeconds
    ) {}

    public record ItemSnapshot(int index, String name, String description) {}
}
