package com.debate.game.Ingame;

import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.websocket.GameActionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** S05: RESULT is terminal for gameplay commands and retains decisions in memory. */
class RoomGameStateFinalizationTest {
    private RoomGameState state(int count) {
        List<RoomStatePayload.ItemPayload> items = count == 1
                ? List.of(new RoomStatePayload.ItemPayload(1, "물품A", "설명"))
                : List.of(new RoomStatePayload.ItemPayload(1, "물품A", "설명"),
                          new RoomStatePayload.ItemPayload(2, "물품B", "설명"));
        RoomGameState state = new RoomGameState("ABC123", items,
                count == 1 ? List.of(1L) : List.of(1L, 2L),
                new RoomStatePayload.RoomSettingsPayload(count, 300, 1),
                List.of(new RoomGameState.PlayerState(11L, "찬성", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "반대", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "사회자", LobbyRole.MODERATOR, true, true)));
        return state;
    }

    private void getToDecision(RoomGameState state) {
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.PRO, 1_000L);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
    }

    @Test
    void lastDecisionEndsGameAndRetainsResult() {
        RoomGameState state = state(1);
        getToDecision(state);
        state.decideItem(13L, true, 3_000L);
        assertEquals(GamePhase.RESULT, state.getPhase());
        assertNull(state.getCurrentItem());
        assertEquals(1, state.getResults().size());
        assertEquals("물품A", state.getResults().getFirst().itemName());
        assertTrue(state.getResults().getFirst().accepted());
        assertNull(state.getItemEndsAt());
        assertNull(state.getSpeech());
        assertTrue(state.handFor(11L).isEmpty());
    }

    @Test
    void nextDecisionDoesNotFinishEarlyAndLastProducesAllResults() {
        RoomGameState state = state(2);
        getToDecision(state);
        state.decideItem(13L, true, 3_000L);
        assertEquals(GamePhase.VOTING, state.getPhase());
        assertEquals("물품B", state.getCurrentItem().name());
        getToDecision(state);
        state.decideItem(13L, false, 4_000L);
        assertEquals(GamePhase.RESULT, state.getPhase());
        assertEquals(2, state.getResults().size());
        assertTrue(state.getResults().get(0).accepted());
        assertFalse(state.getResults().get(1).accepted());
    }

    @Test
    void finalResultBlocksRepeatVoteDecisionSpeechAndModeratorChat() {
        RoomGameState state = state(1);
        getToDecision(state);
        state.decideItem(13L, false, 3_000L);
        assertThrows(GameActionException.class, () -> state.decideItem(13L, true, 4_000L));
        assertThrows(GameActionException.class, () -> state.vote(11L, Stance.CON, 4_000L));
        assertThrows(GameActionException.class, () -> state.startSpeech(11L, 100L, 4_000L));
        assertThrows(GameActionException.class, () -> state.requestRebuttal(11L, 100L, 4_000L));
        assertThrows(GameActionException.class, () -> state.useCard(11L, 100L));
        assertThrows(GameActionException.class, () -> state.sendChat(13L, "종료 후 채팅", 4_000L));
        assertEquals(GamePhase.RESULT, state.getPhase());
        assertEquals(1, state.getResults().size());
    }

    @Test
    void resultCannotBeChangedByTimerOrManualDebateEnd() {
        RoomGameState state = state(1);
        getToDecision(state);
        state.decideItem(13L, true, 3_000L);
        assertEquals(RoomGameState.TimerOutcome.NONE, state.expireTimers(1_000_000L));
        assertFalse(state.finishDebateIfActive());
        assertThrows(GameActionException.class, () -> state.endDebate(13L));
        assertEquals(1, state.getResults().size());
    }
}
