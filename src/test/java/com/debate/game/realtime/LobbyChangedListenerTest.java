package com.debate.game.realtime;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class LobbyChangedListenerTest {
    @Test
    void broadcastsAfterSuccessfulGuestLeave() {
        var sockets = mock(LobbyWebSocketHandler.class);
        var listener = new LobbyChangedListener(sockets);
        listener.afterCommit(new LobbyChangedEvent("ABC123", 12L, false));
        var order = inOrder(sockets);
        order.verify(sockets).closePlayer("ABC123", 12L);
        order.verify(sockets).broadcastRoomState("ABC123");
        order.verifyNoMoreInteractions();
    }

    @Test
    void hostLeaveClosesInsteadOfBroadcastingSnapshot() {
        var sockets = mock(LobbyWebSocketHandler.class);
        new LobbyChangedListener(sockets).afterCommit(new LobbyChangedEvent("ABC123", null, true));
        verify(sockets).closeRoom("ABC123");
        verifyNoMoreInteractions(sockets);
    }
}
