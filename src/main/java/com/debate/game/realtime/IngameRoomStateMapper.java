package com.debate.game.realtime;

import com.debate.game.Ingame.RoomGameState;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.Stance;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/** Maps in-memory game state to a recipient-specific WebSocket snapshot. */
@Component
public class IngameRoomStateMapper {
    private final Clock clock;

    public IngameRoomStateMapper() {
        this(Clock.systemUTC());
    }

    IngameRoomStateMapper(Clock clock) {
        this.clock = clock;
    }

    public RoomStatePayload map(RoomGameState state, Long recipientId) {
        synchronized (state) {
            boolean voting = "VOTING".equals(state.getPhase().name());
            List<RoomStatePayload.PlayerPayload> players = state.getPlayers().values().stream()
                    .map(player -> {
                        Stance stance = player.getStance();
                        if (voting && !player.getId().equals(recipientId)) {
                            stance = null;
                        }
                        return new RoomStatePayload.PlayerPayload(
                                player.getId().toString(),
                                player.getNickname(),
                                player.getRole() == null ? null : player.getRole().name(),
                                player.isReady(),
                                stance == null ? null : stance.name(),
                                player.isHasVoted(),
                                player.isHost()
                        );
                    })
                    .toList();

            return new RoomStatePayload(
                    state.getRoomCode(),
                    clock.millis(),
                    state.getPhase().name(),
                    players,
                    state.getSettings(),
                    state.getCurrentItem(),
                    state.getItemEndsAt(),
                    state.getSpeech(),
                    state.getLastSpeakerId() == null ? null : state.getLastSpeakerId().toString(),
                    state.getPendingRebuttal(),
                    state.getEffect(),
                    List.copyOf(state.getResults())
            );
        }
    }
}
