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

class RoomGameStateDebateFinishTest {

    private RoomGameState newState() {
        return new RoomGameState("ABC123",
                List.of(new RoomStatePayload.ItemPayload(1, "물품", "설명")),
                List.of(101L),
                new RoomStatePayload.RoomSettingsPayload(1, 300, 3),
                List.of(
                        new RoomGameState.PlayerState(11L, "찬성", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "반대", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "사회자", LobbyRole.MODERATOR, true, true)));
    }

    private RoomGameState debateState() {
        RoomGameState state = newState();
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.CON, 1_000L);
        return state;
    }

    @Test
    void manualEndClosesDebateAndResetsTransientState() {
        RoomGameState state = debateState();
        state.giveCards(11L, List.of(new CardPayload(1L, "A", "A", "설명", false)));
        state.giveCards(12L, List.of(new CardPayload(2L, "B", "B", "설명", false)));
        state.startSpeech(11L, 1L, 2_000L);
        state.requestRebuttal(12L, 2L, 3_000L);
        assertTrue(state.handFor(12L).get(0).used());

        assertTrue(state.endDebate(13L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertNull(state.getItemEndsAt());
        assertNull(state.getSpeech());
        assertNull(state.getLastSpeakerId());
        assertNull(state.getPendingRebuttal());
        assertNull(state.getPendingRebuttalCardId());
        assertFalse(state.handFor(12L).get(0).used(), "미승인 반론의 카드는 반환해야 함");
        assertTrue(state.handFor(11L).get(0).used(), "이미 발언한 카드의 사용 기록은 유지");
    }

    @Test
    void duplicateFinishIsIdempotentEvenFromDifferentTriggers() {
        RoomGameState state = debateState();
        assertTrue(state.finishDebateIfActive());
        assertFalse(state.finishDebateIfActive());
        assertFalse(state.endDebate(13L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertNull(state.getItemEndsAt());
        assertEquals(0, state.getResults().size());
    }

    @Test
    void simultaneousTerminationTriggersChangePhaseExactlyOnce() throws Exception {
        RoomGameState state = debateState();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return state.finishDebateIfActive();
                }));
            }
            start.countDown();
            int transitioned = 0;
            for (var result : futures) {
                if (result.get(5, java.util.concurrent.TimeUnit.SECONDS)) transitioned++;
            }
            assertEquals(1, transitioned);
            assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void triggerBeforeDebateDoesNothingAndManualEndRejectsInvalidPhase() {
        RoomGameState state = newState();
        assertFalse(state.finishDebateIfActive());
        assertEquals(GamePhase.VOTING, state.getPhase());
        assertThrows(GameActionException.class, () -> state.endDebate(13L));
        assertEquals(GamePhase.VOTING, state.getPhase());
    }

    @Test
    void onlyModeratorMayManuallyEndDebate() {
        RoomGameState state = debateState();
        assertEquals("INVALID", assertThrows(GameActionException.class,
                () -> state.endDebate(11L)).getCode());
        assertEquals("PLAYER_NOT_IN_ROOM", assertThrows(GameActionException.class,
                () -> state.endDebate(99L)).getCode());
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertNotNull(state.getItemEndsAt());
    }

    @Test
    void finishingMakesItemDecisionPossibleAndBlocksNewSpeech() {
        RoomGameState state = debateState();
        assertTrue(state.endDebate(13L));
        assertEquals(-1, state.validateItemDecisionAndGetNextIndex(13L));
        assertThrows(GameActionException.class, () -> state.startSpeech(11L, 3L, 3_000L));
        state.decideItem(13L, true, 4_000L);
        assertEquals(GamePhase.RESULT, state.getPhase());
        assertFalse(state.finishDebateIfActive());
    }
}
