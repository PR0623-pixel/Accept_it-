package com.debate.game.Ingame;

import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RoomGameStateCardExhaustionTest {
    private static final long START = 1_000L;

    private RoomGameState votingState(int rounds) {
        List<RoomStatePayload.ItemPayload> items = java.util.stream.IntStream.rangeClosed(1, rounds)
                .mapToObj(i -> new RoomStatePayload.ItemPayload(i, "물품" + i, "설명"))
                .toList();
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, rounds).boxed().toList();
        return new RoomGameState("ABC123", items, ids,
                new RoomStatePayload.RoomSettingsPayload(rounds, 300, 2),
                List.of(
                        new RoomGameState.PlayerState(11L, "찬성", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "반대", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "사회자", LobbyRole.MODERATOR, true, true)),
                20, 10);
    }

    private RoomGameState debate(int rounds, int proCount, int conCount) {
        RoomGameState state = votingState(rounds);
        state.vote(11L, Stance.PRO, START);
        state.vote(12L, Stance.CON, START);
        if (proCount > 0) state.giveCards(11L, cards(100L, proCount));
        if (conCount > 0) state.giveCards(12L, cards(200L, conCount));
        return state;
    }

    private List<CardPayload> cards(long base, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new CardPayload(base + i, "K" + i, "제목" + i, "설명" + i, false))
                .toList();
    }

    @Test
    void oneSideLastCardEndsDebateAfterSpeakerEndsEvenWhenOtherSideHasCards() {
        RoomGameState state = debate(1, 1, 3);
        assertFalse(state.hasExhaustedSide());
        state.startSpeech(11L, 100L, 2_000L);
        assertTrue(state.hasExhaustedSide());
        assertFalse(state.finishDebateIfCardsExhausted(), "진행 중인 마지막 발언 보장");
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertTrue(state.endSpeech(11L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertNull(state.getItemEndsAt());
        assertTrue(state.handFor(11L).get(0).used());
        assertEquals(3, state.handFor(12L).stream().filter(card -> !card.used()).count());
        assertFalse(state.finishDebateIfCardsExhausted());
    }

    @Test
    void otherSideLastCardAlsoEndsDebate() {
        RoomGameState state = debate(1, 3, 1);
        state.startSpeech(12L, 200L, 2_000L);
        assertTrue(state.endSpeech(12L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
    }

    @Test
    void remainingCardsOnBothSidesKeepDebateGoing() {
        RoomGameState state = debate(1, 2, 2);
        state.startSpeech(11L, 100L, 2_000L);
        assertFalse(state.endSpeech(11L));
        assertFalse(state.hasExhaustedSide());
        assertEquals(GamePhase.DEBATE, state.getPhase());
        state.startSpeech(12L, 200L, 3_000L);
        assertFalse(state.endSpeech(12L));
        assertEquals(GamePhase.DEBATE, state.getPhase());
    }

    @Test
    void timerExpiryOfLastSpeechClosesDebateRatherThanOnlyReleasingFloor() {
        RoomGameState state = debate(1, 1, 2);
        state.startSpeech(11L, 100L, 2_000L); // ends at 22_000
        assertEquals(RoomGameState.TimerOutcome.NONE, state.expireTimers(21_999L));
        assertEquals(RoomGameState.TimerOutcome.DEBATE_FINISHED, state.expireTimers(22_000L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertEquals(RoomGameState.TimerOutcome.NONE, state.expireTimers(22_001L));
    }

    @Test
    void pendingRebuttalDefersExhaustionUntilModeratorDecision() {
        RoomGameState state = debate(1, 1, 1);
        state.startSpeech(11L, 100L, 2_000L);
        state.requestRebuttal(12L, 200L, 3_000L);
        assertTrue(state.hasExhaustedSide());
        assertFalse(state.endSpeech(11L));
        assertNotNull(state.getPendingRebuttal());
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertTrue(state.decideRebuttal(13L, false, 4_000L),
                "반론을 묵살해도 발언측 PRO가 모두 소진되었으면 종료");
        assertFalse(state.handFor(12L).get(0).used());
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
    }

    @Test
    void deniedRebuttalRestoresLastCardAndContinuesWhenOtherSideHasSpare() {
        RoomGameState state = debate(1, 2, 1);
        state.startSpeech(11L, 100L, 2_000L);
        state.requestRebuttal(12L, 200L, 3_000L);
        assertTrue(state.hasExhaustedSide());
        assertFalse(state.decideRebuttal(13L, false, 4_000L));
        assertFalse(state.handFor(12L).get(0).used());
        assertFalse(state.hasExhaustedSide());
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertFalse(state.endSpeech(11L));
        assertEquals(GamePhase.DEBATE, state.getPhase());
    }

    @Test
    void approvedLastRebuttalFinishesOnlyAfterRebuttalSpeech() {
        RoomGameState state = debate(1, 2, 1);
        state.startSpeech(11L, 100L, 2_000L);
        state.requestRebuttal(12L, 200L, 3_000L);
        assertFalse(state.decideRebuttal(13L, true, 4_000L));
        assertEquals("REBUTTAL", state.getSpeech().kind());
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertTrue(state.endSpeech(12L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertTrue(state.handFor(12L).get(0).used(), "승인된 반론 카드는 복구하지 않음");
    }

    @Test
    void deniedRebuttalCanFinishImmediatelyWhenOriginalSpeechAlreadyExpired() {
        RoomGameState state = debate(1, 1, 1);
        state.startSpeech(11L, 100L, 2_000L);
        state.requestRebuttal(12L, 200L, 3_000L);
        assertEquals(RoomGameState.TimerOutcome.SPEECH_EXPIRED, state.expireTimers(22_000L));
        assertNull(state.getSpeech());
        assertNotNull(state.getPendingRebuttal());
        assertTrue(state.decideRebuttal(13L, false, 22_001L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertFalse(state.handFor(12L).get(0).used());
    }

    @Test
    void missingHandsDoNotCausePrematureExhaustionDuringDeal() {
        RoomGameState state = debate(1, 0, 0);
        assertFalse(state.hasExhaustedSide());
        assertFalse(state.finishDebateIfCardsExhausted());
        state.giveCards(11L, cards(100L, 1));
        assertFalse(state.hasExhaustedSide());
        assertFalse(state.finishDebateIfCardsExhausted());
    }

    @Test
    void eachItemUsesFreshHandsAndExhaustionDoesNotCarryOver() {
        RoomGameState state = debate(2, 1, 2);
        state.startSpeech(11L, 100L, 2_000L);
        assertTrue(state.endSpeech(11L));
        state.decideItem(13L, true, 3_000L);
        assertEquals(GamePhase.VOTING, state.getPhase());
        assertTrue(state.handFor(11L).isEmpty());
        assertTrue(state.handFor(12L).isEmpty());
        assertFalse(state.hasExhaustedSide());
        state.vote(11L, Stance.CON, 4_000L);
        state.vote(12L, Stance.PRO, 4_000L);
        assertFalse(state.hasExhaustedSide());
        state.giveCards(11L, cards(300L, 2));
        state.giveCards(12L, cards(400L, 2));
        assertFalse(state.hasExhaustedSide());
        assertEquals(GamePhase.DEBATE, state.getPhase());
    }

    @Test
    void equalStanceSkipsDebateAndNeverTriggersCardExhaustion() {
        RoomGameState state = votingState(1);
        state.vote(11L, Stance.PRO, START);
        state.vote(12L, Stance.PRO, START);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertFalse(state.hasExhaustedSide());
        assertFalse(state.finishDebateIfCardsExhausted());
    }

    @Test
    void periodicCheckCatchesIdleExhaustionWithoutASecondAction() {
        RoomGameState state = debate(1, 1, 2);
        state.useCard(11L, 100L);
        assertNull(state.getSpeech());
        assertEquals(RoomGameState.TimerOutcome.DEBATE_FINISHED, state.expireTimers(2_000L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertEquals(RoomGameState.TimerOutcome.NONE, state.expireTimers(2_001L));
    }

    @Test
    void concurrentCardExhaustionFinalizersChangePhaseAtMostOnce() throws Exception {
        RoomGameState state = debate(1, 1, 2);
        state.startSpeech(11L, 100L, 2_000L);
        assertTrue(state.endSpeech(11L));
        var threads = Executors.newFixedThreadPool(6);
        var latch = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(threads.submit(() -> {
                    latch.await();
                    return state.finishDebateIfCardsExhausted();
                }));
            }
            latch.countDown();
            for (Future<Boolean> future : futures) {
                assertFalse(future.get(3, TimeUnit.SECONDS));
            }
            assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        } finally {
            threads.shutdownNow();
        }
    }
}
