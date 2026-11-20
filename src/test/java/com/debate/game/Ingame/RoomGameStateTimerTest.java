package com.debate.game.Ingame;

import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.websocket.GameActionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.debate.game.Ingame.RoomGameState.TimerOutcome.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomGameStateTimerTest {
    private RoomGameState votingState(int count) {
        List<RoomStatePayload.ItemPayload> items = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new RoomStatePayload.ItemPayload(i, "물품" + i, "설명"))
                .toList();
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, count).boxed().toList();
        return new RoomGameState("ABC123", items, ids,
                new RoomStatePayload.RoomSettingsPayload(count, 10, 2),
                List.of(
                        new RoomGameState.PlayerState(11L, "PRO", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "CON", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "MOD", LobbyRole.MODERATOR, true, true)),
                3, 2);
    }

    private RoomGameState debateState() {
        RoomGameState state = votingState(1);
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.CON, 1_000L);
        state.giveCards(11L, List.of(new CardPayload(1L, "A", "A", "A", false)));
        state.giveCards(12L, List.of(new CardPayload(2L, "B", "B", "B", false)));
        return state;
    }

    @Test
    void itemDeadlineAtBoundaryFinishesDebateExactlyOnce() {
        RoomGameState state = debateState();
        assertEquals(11_000L, state.getItemEndsAt());
        assertEquals(NONE, state.expireTimers(10_999L));
        assertEquals(DEBATE_FINISHED, state.expireTimers(11_000L));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertNull(state.getItemEndsAt());
        assertEquals(NONE, state.expireTimers(99_000L));
    }

    @Test
    void speechDeadlineReleasesFloorButDoesNotEndDebate() {
        RoomGameState state = debateState();
        state.startSpeech(11L, 1L, 2_000L);
        assertEquals(5_000L, state.getSpeech().endsAt());
        assertEquals(NONE, state.expireTimers(4_999L));
        assertEquals(SPEECH_EXPIRED, state.expireTimers(5_000L));
        assertNull(state.getSpeech());
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertEquals(11L, state.getLastSpeakerId());
        assertEquals(NONE, state.expireTimers(6_000L));
        assertThrows(GameActionException.class, () -> state.endSpeech(11L));
        state.startSpeech(12L, 2L, 6_000L);
        assertEquals("12", state.getSpeech().speakerId(), "시간 만료 후 상대 객원에게 발언권이 돌아와야 함");
    }

    @Test
    void approvedRebuttalUsesRoomSettingAndExpires() {
        RoomGameState state = debateState();
        state.startSpeech(11L, 1L, 2_000L);
        state.requestRebuttal(12L, 2L, 2_500L);
        state.decideRebuttal(13L, true, 3_000L);
        assertEquals("REBUTTAL", state.getSpeech().kind());
        assertEquals(5_000L, state.getSpeech().endsAt());
        assertEquals(NONE, state.expireTimers(4_999L));
        assertEquals(SPEECH_EXPIRED, state.expireTimers(5_000L));
        assertNull(state.getSpeech());
        assertTrue(state.handFor(12L).get(0).used(), "승인된 반론 카드는 반환하지 않음");
    }

    @Test
    void itemDeadlineOverridesActiveSpeechAndRestoresUnresolvedRebuttalCard() {
        RoomGameState state = debateState();
        state.startSpeech(11L, 1L, 10_000L);
        state.requestRebuttal(12L, 2L, 10_500L);
        assertTrue(state.handFor(12L).get(0).used());
        assertEquals(DEBATE_FINISHED, state.expireTimers(11_000L));
        assertNull(state.getSpeech());
        assertNull(state.getPendingRebuttal());
        assertNull(state.getPendingRebuttalCardId());
        assertFalse(state.handFor(12L).get(0).used());
        assertTrue(state.handFor(11L).get(0).used());
    }

    @Test
    void speechExpiryPreservesPendingModeratorRebuttalDecision() {
        RoomGameState state = debateState();
        state.startSpeech(11L, 1L, 2_000L);
        state.requestRebuttal(12L, 2L, 2_500L);
        assertEquals(SPEECH_EXPIRED, state.expireTimers(5_000L));
        assertNull(state.getSpeech());
        assertNotNull(state.getPendingRebuttal());
        state.decideRebuttal(13L, false, 5_100L);
        assertFalse(state.handFor(12L).get(0).used());
        assertNull(state.getPendingRebuttal());
        assertEquals(GamePhase.DEBATE, state.getPhase());
    }

    @Test
    void deadlineRejectsNewSpeechAndRebuttalBeforeNextScheduledPoll() {
        RoomGameState state = debateState();
        GameActionException lateSpeech = assertThrows(GameActionException.class,
                () -> state.startSpeech(11L, 1L, 11_000L));
        assertEquals("TIME_EXPIRED", lateSpeech.getCode());
        assertFalse(state.handFor(11L).get(0).used());
        state.startSpeech(11L, 1L, 2_000L);
        assertEquals("TIME_EXPIRED", assertThrows(GameActionException.class,
                () -> state.requestRebuttal(12L, 2L, 11_000L)).getCode());
        assertFalse(state.handFor(12L).get(0).used());
        state.requestRebuttal(12L, 2L, 3_000L);
        assertEquals("TIME_EXPIRED", assertThrows(GameActionException.class,
                () -> state.decideRebuttal(13L, true, 11_000L)).getCode());
    }

    @Test
    void expiredSpeechCannotSendChatOrBeRebuttedEvenBeforePoll() {
        RoomGameState state = debateState();
        state.startSpeech(11L, 1L, 2_000L); // expires at 5_000
        assertEquals("NOT_YOUR_TURN", assertThrows(GameActionException.class,
                () -> state.sendChat(11L, "늦은 메시지", 5_000L)).getCode());
        assertEquals("NO_TARGET", assertThrows(GameActionException.class,
                () -> state.requestRebuttal(12L, 2L, 5_000L)).getCode());
        assertFalse(state.handFor(12L).get(0).used());
    }

    @Test
    void doesNotExpireVotingDecisionOrResult() {
        RoomGameState state = votingState(1);
        assertEquals(NONE, state.expireTimers(Long.MAX_VALUE));
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.PRO, 1_000L);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertEquals(NONE, state.expireTimers(Long.MAX_VALUE));
        state.decideItem(13L, true, 2_000L);
        assertEquals(GamePhase.RESULT, state.getPhase());
        assertEquals(NONE, state.expireTimers(Long.MAX_VALUE));
    }

    @Test
    void nextRoundDeadlineIsFreshAndOldDeadlineDoesNotInterfere() {
        RoomGameState state = votingState(2);
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.CON, 1_000L);
        assertEquals(DEBATE_FINISHED, state.expireTimers(11_000L));
        state.decideItem(13L, true, 12_000L);
        assertEquals(GamePhase.VOTING, state.getPhase());
        assertEquals(NONE, state.expireTimers(999_999L));
        state.vote(11L, Stance.PRO, 30_000L);
        state.vote(12L, Stance.CON, 30_000L);
        assertEquals(40_000L, state.getItemEndsAt());
        assertEquals(NONE, state.expireTimers(39_999L));
        assertEquals(DEBATE_FINISHED, state.expireTimers(40_000L));
    }

    @Test
    void manualEndWinsAgainstLaterTimerSweep() {
        RoomGameState state = debateState();
        assertTrue(state.endDebate(13L));
        assertEquals(NONE, state.expireTimers(11_000L));
    }
}
