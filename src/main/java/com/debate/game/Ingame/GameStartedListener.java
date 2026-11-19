package com.debate.game.Ingame;

import com.debate.game.realtime.LobbyWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GameStartedListener {
    private final RoomGameStateStore gameStateStore;
    private final LobbyWebSocketHandler sockets;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(GameStartedEvent event) {
        gameStateStore.save(event.state());
        sockets.broadcastRoomState(event.state().getRoomCode());
    }
}