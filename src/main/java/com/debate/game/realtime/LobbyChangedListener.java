package com.debate.game.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Never send uncommitted room state or broadcast rolled-back mutations. */
@Component
@RequiredArgsConstructor
public class LobbyChangedListener {
    private final LobbyWebSocketHandler sockets;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(LobbyChangedEvent event) {
        if (event.roomClosed()) {
            sockets.closeRoom(event.roomCode());
            return;
        }
        if (event.leftPlayerId() != null) sockets.closePlayer(event.roomCode(), event.leftPlayerId());
        sockets.broadcastRoomState(event.roomCode());
    }
}
