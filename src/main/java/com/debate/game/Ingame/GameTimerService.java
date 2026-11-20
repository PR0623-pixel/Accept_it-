package com.debate.game.Ingame;

import com.debate.game.realtime.LobbyWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Server-authoritative, idempotent deadline checks for all active game rooms. */
@Component
@RequiredArgsConstructor
public class GameTimerService {
    private static final Logger log = LoggerFactory.getLogger(GameTimerService.class);

    private final RoomGameStateStore states;
    private final LobbyWebSocketHandler sockets;

    @Scheduled(fixedDelayString = "${acceptit.game.timers.poll-ms:250}")
    public void pollTimers() {
        pollTimers(System.currentTimeMillis());
    }

    /** Explicit clock argument allows deterministic tests without sleeping. */
    public void pollTimers(long now) {
        for (RoomGameState state : states.snapshot()) {
            // Hold the room lock through broadcast so a later round cannot
            // be reported as the timer result of a previous round.
            synchronized (state) {
                // An in-flight closeRoom may have removed/replaced this entry.
                if (states.find(state.getRoomCode()).orElse(null) != state) {
                    continue;
                }
                try {
                    RoomGameState.TimerOutcome outcome = state.expireTimers(now);
                    switch (outcome) {
                        case DEBATE_FINISHED -> sockets.broadcastDebateFinished(state.getRoomCode());
                        case SPEECH_EXPIRED -> sockets.broadcastRoomState(state.getRoomCode());
                        case NONE -> { }
                    }
                } catch (RuntimeException exception) {
                    // A failure in one room should not prevent other rooms
                    // from having their deadlines checked.
                    log.error("Timer check failed for room {}", state.getRoomCode(), exception);
                }
            }
        }
    }
}
