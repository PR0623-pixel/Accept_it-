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

class GameTimerServiceCardExhaustionTest {
    private RoomGameState state() {
        RoomGameState game = new RoomGameState("ABC123",
                List.of(new RoomStatePayload.ItemPayload(1, "물품", "설명")),
                List.of(1L), new RoomStatePayload.RoomSettingsPayload(1, 300, 2),
                List.of(
                        new RoomGameState.PlayerState(11L, "PRO", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "CON", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "MOD", LobbyRole.MODERATOR, true, true)),
                20, 10);
        game.vote(11L, Stance.PRO, 1_000L);
        game.vote(12L, Stance.CON, 1_000L);
        game.giveCards(11L, List.of(new CardPayload(100L, "P", "P", "P", false)));
        game.giveCards(12L, List.of(
                new CardPayload(200L, "C", "C", "C", false),
                new CardPayload(201L, "C2", "C2", "C2", false)));
        return game;
    }

    @Test
    void serverTimerEndsLastCardSpeechAndNotifiesOnlyOnce() {
        RoomGameStateStore store = new RoomGameStateStore();
        LobbyWebSocketHandler sockets = mock(LobbyWebSocketHandler.class);
        GameTimerService timer = new GameTimerService(store, sockets);
        RoomGameState state = state();
        state.startSpeech(11L, 100L, 2_000L);
        store.save(state);

        timer.pollTimers(21_999L);
        verifyNoInteractions(sockets);
        timer.pollTimers(22_000L);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        verify(sockets, times(1)).broadcastDebateFinished("ABC123");
        verify(sockets, never()).broadcastRoomState("ABC123");
        timer.pollTimers(22_001L);
        verify(sockets, times(1)).broadcastDebateFinished("ABC123");
    }

    @Test
    void itemDeadlineStillOverridesLastCardSpeech() {
        RoomGameStateStore store = new RoomGameStateStore();
        LobbyWebSocketHandler sockets = mock(LobbyWebSocketHandler.class);
        GameTimerService timer = new GameTimerService(store, sockets);
        RoomGameState state = state();
        state.startSpeech(11L, 100L, 300_000L); // still active at 301_000 item deadline
        store.save(state);

        timer.pollTimers(301_000L);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        verify(sockets, times(1)).broadcastDebateFinished("ABC123");
        assertNull(state.getSpeech());
    }
}
