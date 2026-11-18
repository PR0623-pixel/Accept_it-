package com.debate.game.websocket;

import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.dto.websocket.InitialSyncPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.dto.websocket.RoomStatePayload.PlayerPayload;
import com.debate.game.dto.websocket.RoomStatePayload.RoomSettingsPayload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LobbyInitialSyncProvider implements InitialSyncProvider {

    private final com.debate.game.service.RoomService roomService;

    public LobbyInitialSyncProvider(
            com.debate.game.service.RoomService roomService
    ) {
        this.roomService = roomService;
    }

    @Override
    public Optional<InitialSyncPayload> load(String roomCode, String playerId) {
        return roomService.getLobbyForPlayer(roomCode, playerId)
                .map(this::toPayload);
    }

    private InitialSyncPayload toPayload(LobbySnapshotResponse lobby) {
        List<PlayerPayload> players = lobby.players().stream()
                .map(player -> new PlayerPayload(
                        player.id(),
                        player.nickname(),
                        player.role(),
                        player.ready(),
                        null,   // 로비에서는 stance 없음
                        false,  // 로비에서는 투표 없음
                        player.isHost()
                ))
                .toList();

        RoomStatePayload roomState = new RoomStatePayload(
                lobby.roomCode(),
                System.currentTimeMillis(),
                "LOBBY",
                players,
                new RoomSettingsPayload(
                        lobby.settings().maxItems(),
                        lobby.settings().itemSeconds(),
                        lobby.settings().cardsPerItem()
                ),
                null, // currentItem
                null, // itemEndsAt
                null, // speech
                null, // lastSpeakerId
                null, // pendingRebuttal
                null, // effect
                List.of() // results
        );

        return new InitialSyncPayload(roomState, List.of(), List.of());
    }
}