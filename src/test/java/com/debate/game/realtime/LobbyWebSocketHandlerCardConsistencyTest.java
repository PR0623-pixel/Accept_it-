package com.debate.game.realtime;

import com.debate.game.Ingame.GameTimerService;
import com.debate.game.Ingame.RoomGameState;
import com.debate.game.Ingame.RoomGameStateStore;
import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.service.RoomService;
import com.debate.game.service.SituationCardService;
import com.debate.game.websocket.GameActionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** S04: persistent card state and room-local HAND must never diverge on failed actions. */
class LobbyWebSocketHandlerCardConsistencyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RoomGameStateStore store = new RoomGameStateStore();
    private final SituationCardService cards = mock(SituationCardService.class);
    private final LobbyStateReader reader = mock(LobbyStateReader.class);
    private final LobbyWebSocketHandler handler = new LobbyWebSocketHandler(
            mock(RoomService.class), reader, new LobbyRoomStateMapper(),
            new IngameRoomStateMapper(), store, json, cards);

    private RoomGameState debate() {
        RoomGameState state = new RoomGameState("ABC123",
                List.of(new RoomStatePayload.ItemPayload(1, "물품A", "설명A"),
                        new RoomStatePayload.ItemPayload(2, "물품B", "설명B")),
                List.of(1L, 2L),
                new RoomStatePayload.RoomSettingsPayload(2, 300, 2),
                List.of(new RoomGameState.PlayerState(11L, "찬성", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "반대", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "사회자", LobbyRole.MODERATOR, true, true)),
                60, 30);
        long now = System.currentTimeMillis();
        state.vote(11L, Stance.PRO, now);
        state.vote(12L, Stance.CON, now);
        state.giveCards(11L, List.of(card(101), card(102)));
        state.giveCards(12L, List.of(card(201), card(202)));
        store.save(state);
        return state;
    }

    private CardPayload card(long id) {
        return new CardPayload(id, "키워드" + id, "카드" + id, "설명", false);
    }

    private WebSocketSession connect(long id) throws Exception {
        when(reader.read("ABC123")).thenReturn(new LobbySnapshotResponse(
                "ABC123", "13", "LOBBY",
                List.of(new LobbySnapshotResponse.LobbyPlayer("11", "찬성", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("12", "반대", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("13", "사회자", "MODERATOR", true, true)),
                new LobbySnapshotResponse.LobbySettings(2, 300, 2, 60, 30)));
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("socket-" + id);
        when(socket.getUri()).thenReturn(URI.create("ws://localhost/ws/rooms/ABC123?playerId=" + id));
        when(socket.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(socket);
        clearInvocations(socket);
        return socket;
    }

    private void send(WebSocketSession socket, String type, String payload) throws Exception {
        handler.handleTextMessage(socket, new TextMessage(
                "{\"type\":\"" + type + "\",\"payload\":" + payload + "}"));
    }

    private List<JsonNode> messages(WebSocketSession socket) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(m -> {
            try { return json.readTree(m.getPayload()); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }).toList();
    }

    @Test
    void busySpeechFailsBeforeDatabaseMark() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession con = connect(12);
        send(con, "START_SPEECH", "{\"cardId\":201}");
        verify(cards, never()).markUsed(anyString(), anyLong(), any());
        assertFalse(state.handFor(12L).get(0).used());
        assertEquals("ERROR", messages(con).get(0).path("type").asText());
    }

    @Test
    void invalidAndRepeatedSpeechOnlyPersistFirstValidUse() throws Exception {
        RoomGameState state = debate();
        WebSocketSession pro = connect(11);
        send(pro, "START_SPEECH", "{\"cardId\":201}"); // other side's card
        verify(cards, never()).markUsed(anyString(), anyLong(), any());
        send(pro, "START_SPEECH", "{\"cardId\":101}");
        send(pro, "START_SPEECH", "{\"cardId\":102}"); // floor busy
        verify(cards, times(1)).markUsed("ABC123", 101L, DebateRole.PRO);
        assertTrue(state.handFor(11L).get(0).used());
        assertFalse(state.handFor(11L).get(1).used());
        assertEquals("SPEECH", state.getSpeech().kind());
    }

    @Test
    void databaseFailureDoesNotConsumeOrBeginSpeech() throws Exception {
        RoomGameState state = debate();
        WebSocketSession pro = connect(11);
        doThrow(new GameActionException("CARD_REQUIRED", "DB 사용 실패"))
                .when(cards).markUsed("ABC123", 101L, DebateRole.PRO);
        send(pro, "START_SPEECH", "{\"cardId\":101}");
        assertNull(state.getSpeech());
        assertFalse(state.handFor(11L).get(0).used());
        assertEquals("ERROR", messages(pro).get(0).path("type").asText());
    }

    @Test
    void rebuttalPersistsReservationAndDenialRestoresOwnerHandAndDatabase() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession pro = connect(11);
        WebSocketSession con = connect(12);
        WebSocketSession mod = connect(13);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        verify(cards).markUsed("ABC123", 201L, DebateRole.CON);
        assertTrue(state.handFor(12L).get(0).used());
        clearInvocations(pro, con, mod);
        send(mod, "DECIDE_REBUTTAL", "{\"approve\":false}");
        verify(cards).restore("ABC123", 201L, DebateRole.CON);
        assertFalse(state.handFor(12L).get(0).used());
        assertNull(state.getPendingRebuttal());
        assertTrue(messages(con).stream().anyMatch(m ->
                "HAND".equals(m.path("type").asText())
                        && !m.path("payload").get(0).path("used").asBoolean()));
        verify(pro, never()).sendMessage(argThat(m -> m instanceof TextMessage t && t.getPayload().contains("\"type\":\"HAND\"")));
    }

    @Test
    void unauthorizedDenialNeverRestoresDatabaseCard() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession pro = connect(11);
        WebSocketSession con = connect(12);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        send(pro, "DECIDE_REBUTTAL", "{\"approve\":false}");
        verify(cards, never()).restore(anyString(), anyLong(), any());
        assertNotNull(state.getPendingRebuttal());
        assertTrue(state.handFor(12L).get(0).used());
    }

    @Test
    void failedDatabaseRestorePreservesPendingRebuttal() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession con = connect(12);
        WebSocketSession mod = connect(13);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        doThrow(new GameActionException("INVALID", "DB 복구 실패"))
                .when(cards).restore("ABC123", 201L, DebateRole.CON);
        send(mod, "DECIDE_REBUTTAL", "{\"approve\":false}");
        assertNotNull(state.getPendingRebuttal());
        assertTrue(state.handFor(12L).get(0).used());
        assertEquals("ERROR", messages(mod).get(messages(mod).size()-1).path("type").asText());
    }

    @Test
    void invalidAndFailedRebuttalNeverReserveACard() throws Exception {
        RoomGameState state = debate();
        WebSocketSession con = connect(12);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}"); // no target speech
        verify(cards, never()).markUsed(anyString(), anyLong(), any());
        assertFalse(state.handFor(12L).get(0).used());
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        doThrow(new GameActionException("CARD_REQUIRED", "DB 저장 실패"))
                .when(cards).markUsed("ABC123", 201L, DebateRole.CON);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        assertNull(state.getPendingRebuttal());
        assertFalse(state.handFor(12L).get(0).used());
    }

    @Test
    void denialOfRebuttalAfterCardExhaustionPreservesCardPolicy() throws Exception {
        RoomGameState state = debate();
        state.giveCards(11L, List.of(card(101)));
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession con = connect(12);
        WebSocketSession mod = connect(13);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        send(mod, "DECIDE_REBUTTAL", "{\"approve\":false}");
        assertEquals(GamePhase.DEBATE, state.getPhase()); // 11 still speaking
        assertFalse(state.handFor(12L).get(0).used());
        assertTrue(state.handFor(11L).get(0).used());
        // The underlying original speech can now finish and trigger S03.
        WebSocketSession pro = connect(11);
        send(pro, "END_SPEECH", "{}");
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
    }

    @Test
    void manualEndRestoresDbReservationAfterPermissionCheck() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession pro = connect(11);
        WebSocketSession con = connect(12);
        WebSocketSession mod = connect(13);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        send(pro, "END_DEBATE", "{}");
        verify(cards, never()).restore(anyString(), anyLong(), any());
        send(mod, "END_DEBATE", "{}");
        verify(cards).restore("ABC123", 201L, DebateRole.CON);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertFalse(state.handFor(12L).get(0).used());
        send(mod, "END_DEBATE", "{}");
        verify(cards, times(1)).restore("ABC123", 201L, DebateRole.CON);
    }

    @Test
    void timerDeadlineRestoresDbBeforeDiscardingPendingRebuttal() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession con = connect(12);
        connect(13);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        new GameTimerService(store, handler).pollTimers(state.getItemEndsAt() + 1);
        verify(cards).restore("ABC123", 201L, DebateRole.CON);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertNull(state.getPendingRebuttal());
        assertFalse(state.handFor(12L).get(0).used());
    }

    @Test
    void failedDeadlineRestoreRetainsRebuttalForRetry() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession con = connect(12);
        connect(13);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        doThrow(new GameActionException("INVALID", "DB 복구 불가"))
                .when(cards).restore("ABC123", 201L, DebateRole.CON);
        new GameTimerService(store, handler).pollTimers(state.getItemEndsAt() + 1);
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertNotNull(state.getPendingRebuttal());
        assertTrue(state.handFor(12L).get(0).used());
    }

    @Test
    void failedManualRestoreDoesNotAdvanceDebate() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 101L, System.currentTimeMillis());
        WebSocketSession con = connect(12);
        WebSocketSession mod = connect(13);
        send(con, "REQUEST_REBUTTAL", "{\"cardId\":201}");
        doThrow(new GameActionException("INVALID", "DB 복구 불가"))
                .when(cards).restore("ABC123", 201L, DebateRole.CON);
        send(mod, "END_DEBATE", "{}");
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertNotNull(state.getPendingRebuttal());
        assertEquals("ERROR", messages(mod).get(messages(mod).size()-1).path("type").asText());
    }

    @Test
    void oldItemCardCannotBeReusedInNewRound() throws Exception {
        RoomGameState state = debate();
        state.endDebate(13L);
        state.decideItem(13L, true, System.currentTimeMillis());
        state.vote(11L, Stance.CON, System.currentTimeMillis());
        state.vote(12L, Stance.PRO, System.currentTimeMillis());
        state.giveCards(11L, List.of(card(301), card(302)));
        state.giveCards(12L, List.of(card(401), card(402)));
        WebSocketSession pro = connect(11);
        send(pro, "START_SPEECH", "{\"cardId\":101}");
        verify(cards, never()).markUsed(anyString(), anyLong(), any());
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertFalse(state.handFor(11L).get(0).used());
    }

    @Test
    void concurrentDuplicateSpeechRequestsPersistOnlyOnce() throws Exception {
        RoomGameState state = debate();
        WebSocketSession pro = connect(11);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> a = CompletableFuture.runAsync(() -> {
                try { send(pro, "START_SPEECH", "{\"cardId\":101}"); }
                catch (Exception e) { throw new RuntimeException(e); }
            }, executor);
            CompletableFuture<Void> b = CompletableFuture.runAsync(() -> {
                try { send(pro, "START_SPEECH", "{\"cardId\":101}"); }
                catch (Exception e) { throw new RuntimeException(e); }
            }, executor);
            CompletableFuture.allOf(a, b).join();
        } finally {
            executor.shutdownNow();
        }
        verify(cards, times(1)).markUsed("ABC123", 101L, DebateRole.PRO);
        assertTrue(state.handFor(11L).get(0).used());
        assertEquals("SPEECH", state.getSpeech().kind());
    }
}
