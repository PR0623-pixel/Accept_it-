package com.debate.game.Ingame;

import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.realtime.LobbyWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameTimerServiceTest {
    private RoomGameState newDebate(String code, long beginsAt) {
        RoomGameState state = new RoomGameState(code,
                List.of(new RoomStatePayload.ItemPayload(1, "물품", "설명")),
                List.of(101L),
                new RoomStatePayload.RoomSettingsPayload(1, 10, 2),
                List.of(
                        new RoomGameState.PlayerState(11L, "PRO", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "CON", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "MOD", LobbyRole.MODERATOR, true, true)),
                3, 2);
        state.vote(11L, Stance.PRO, beginsAt);
        state.vote(12L, Stance.CON, beginsAt);
        state.giveCards(11L, List.of(new CardPayload(1L, "A", "A", "A", false)));
        return state;
    }

    @Test
    void broadcastsSpeechExpiryOnceAndFinishesLater() {
        RoomGameStateStore store = new RoomGameStateStore();
        LobbyWebSocketHandler sockets = mock(LobbyWebSocketHandler.class);
        GameTimerService timer = new GameTimerService(store, sockets);
        RoomGameState state = newDebate("ABC123", 1_000L);
        state.startSpeech(11L, 1L, 2_000L);
        store.save(state);

        timer.pollTimers(4_999L);
        verifyNoInteractions(sockets);
        timer.pollTimers(5_000L);
        assertNull(state.getSpeech());
        verify(sockets, times(1)).broadcastRoomState("ABC123");
        timer.pollTimers(5_000L);
        verify(sockets, times(1)).broadcastRoomState("ABC123");

        timer.pollTimers(11_000L);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        verify(sockets, times(1)).broadcastDebateFinished("ABC123");
        timer.pollTimers(12_000L);
        verify(sockets, times(1)).broadcastDebateFinished("ABC123");
    }

    @Test
    void onlyExpiredRoomsBroadcastAndDeletedRoomsAreNotVisited() {
        RoomGameStateStore store = new RoomGameStateStore();
        LobbyWebSocketHandler sockets = mock(LobbyWebSocketHandler.class);
        GameTimerService timer = new GameTimerService(store, sockets);
        RoomGameState expired = newDebate("ABC123", 1_000L);
        RoomGameState future = newDebate("XYZ789", 20_000L);
        RoomGameState removed = newDebate("REM123", 1_000L);
        store.save(expired);
        store.save(future);
        store.save(removed);
        store.remove("REM123");

        timer.pollTimers(11_000L);
        assertEquals(GamePhase.MODERATOR_DECISION, expired.getPhase());
        assertEquals(GamePhase.DEBATE, future.getPhase());
        assertEquals(GamePhase.DEBATE, removed.getPhase());
        verify(sockets, times(1)).broadcastDebateFinished("ABC123");
        verify(sockets, never()).broadcastDebateFinished("XYZ789");
        verify(sockets, never()).broadcastDebateFinished("REM123");
    }

    @Test
    void repeatedSweepsAfterManualTerminationDoNothing() {
        RoomGameStateStore store = new RoomGameStateStore();
        LobbyWebSocketHandler sockets = mock(LobbyWebSocketHandler.class);
        GameTimerService timer = new GameTimerService(store, sockets);
        RoomGameState state = newDebate("ABC123", 1_000L);
        store.save(state);
        assertTrue(state.endDebate(13L));
        timer.pollTimers(11_000L);
        verifyNoInteractions(sockets);
    }
}
