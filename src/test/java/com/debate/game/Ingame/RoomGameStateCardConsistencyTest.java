package com.debate.game.Ingame;

import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.websocket.GameActionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoomGameStateCardConsistencyTest {
    private CardPayload card(long id) {
        return new CardPayload(id, "키워드", "제목", "설명", false);
    }

    private RoomGameState state() {
        RoomGameState room = new RoomGameState("ABC123",
                List.of(new RoomStatePayload.ItemPayload(1, "물품", "설명")),
                List.of(1L), new RoomStatePayload.RoomSettingsPayload(1, 300, 2),
                List.of(new RoomGameState.PlayerState(11L, "A", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "B", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "M", LobbyRole.MODERATOR, true, true)));
        room.vote(11L, Stance.PRO, 1000);
        room.vote(12L, Stance.CON, 1000);
        return room;
    }

    @Test
    void rejectsIncompleteHandsWithoutChangingEitherGuest() {
        RoomGameState room = state();
        assertThrows(GameActionException.class,
                () -> room.giveHandsForDebate(Map.of(11L, List.of(card(101), card(102)))));
        assertTrue(room.handFor(11L).isEmpty());
        assertTrue(room.handFor(12L).isEmpty());
    }

    @Test
    void rejectsDuplicateCardIdsAcrossSides() {
        RoomGameState room = state();
        assertThrows(GameActionException.class,
                () -> room.giveHandsForDebate(Map.of(
                        11L, List.of(card(101), card(102)),
                        12L, List.of(card(101), card(202)))));
        assertTrue(room.handFor(11L).isEmpty());
        assertTrue(room.handFor(12L).isEmpty());
    }

    @Test
    void failedDealCanUndoFinalVoteAndRetryWithoutLosingFirstVote() {
        RoomGameState room = state();
        room.rollbackUndealtDebateVote(12L);
        assertEquals(GamePhase.VOTING, room.getPhase());
        assertTrue(room.getPlayers().get(11L).isHasVoted());
        assertEquals(Stance.PRO, room.getPlayers().get(11L).getStance());
        assertFalse(room.getPlayers().get(12L).isHasVoted());
        assertNull(room.getPlayers().get(12L).getStance());
        assertNull(room.getItemEndsAt());

        room.vote(12L, Stance.CON, 2000);
        room.giveHandsForDebate(Map.of(11L, List.of(card(101), card(102)),
                12L, List.of(card(201), card(202))));
        assertEquals(GamePhase.DEBATE, room.getPhase());
        assertEquals(2, room.handFor(11L).size());
        assertEquals(2, room.handFor(12L).size());
        assertEquals(302000L, room.getItemEndsAt());
    }

    @Test
    void cannotUndoVoteAfterHandsHaveBeenInstalled() {
        RoomGameState room = state();
        room.giveHandsForDebate(Map.of(11L, List.of(card(101), card(102)),
                12L, List.of(card(201), card(202))));
        assertThrows(IllegalStateException.class, () -> room.rollbackUndealtDebateVote(12L));
    }
}
