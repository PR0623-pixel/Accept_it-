package com.debate.game.realtime;

import com.debate.game.Ingame.RoomGameState;
import com.debate.game.Ingame.RoomGameStateStore;
import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.service.RoomService;
import com.debate.game.service.SituationCardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** S05: DB failure must never publish a false RESULT or change the game state. */
class LobbyWebSocketHandlerItemProgressTest {
    private final RoomService rooms = mock(RoomService.class);
    private final LobbyStateReader reader = mock(LobbyStateReader.class);
    private final RoomGameStateStore store = new RoomGameStateStore();
    private final ObjectMapper mapper = new ObjectMapper();
    private final LobbyWebSocketHandler handler = new LobbyWebSocketHandler(
            rooms, reader, new LobbyRoomStateMapper(), new IngameRoomStateMapper(),
            store, mapper, mock(SituationCardService.class));

    private RoomGameState state(int count) {
        RoomGameState state = new RoomGameState("ABC123",
                count == 1
                        ? List.of(new RoomStatePayload.ItemPayload(1, "첫 물품", "설명"))
                        : List.of(new RoomStatePayload.ItemPayload(1, "첫 물품", "설명"),
                                  new RoomStatePayload.ItemPayload(2, "다음 물품", "설명")),
                count == 1 ? List.of(1L) : List.of(1L, 2L),
                new RoomStatePayload.RoomSettingsPayload(count, 300, 2),
                List.of(new RoomGameState.PlayerState(11L, "객원1", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "객원2", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "사회자", LobbyRole.MODERATOR, true, true)));
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.PRO, 1_000L);
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        store.save(state);
        return state;
    }

    private WebSocketSession connect(long id) throws Exception {
        when(reader.read("ABC123")).thenReturn(new LobbySnapshotResponse("ABC123", "13", "LOBBY",
                List.of(new LobbySnapshotResponse.LobbyPlayer("11", "객원1", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("12", "객원2", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("13", "사회자", "MODERATOR", true, true)),
                new LobbySnapshotResponse.LobbySettings(2, 300, 2, 120, 60)));
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("ws" + id);
        when(socket.getUri()).thenReturn(URI.create("ws://localhost/ws/rooms/ABC123?playerId=" + id));
        when(socket.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(socket);
        clearInvocations(socket);
        return socket;
    }

    private void decide(WebSocketSession socket, boolean accepted) throws Exception {
        handler.handleTextMessage(socket, new TextMessage(
                "{\"type\":\"DECIDE_ITEM\",\"payload\":{\"accept\":" + accepted + "}}"));
    }

    private List<JsonNode> messages(WebSocketSession socket) throws Exception {
        ArgumentCaptor<TextMessage> cap = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, atLeastOnce()).sendMessage(cap.capture());
        return cap.getAllValues().stream().map(message -> {
            try { return mapper.readTree(message.getPayload()); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }).toList();
    }

    @Test
    void successfulFinalDecisionBroadcastsResultAndClearsPrivateStateForEveryone() throws Exception {
        RoomGameState game = state(1);
        WebSocketSession pro = connect(11);
        WebSocketSession con = connect(12);
        WebSocketSession mod = connect(13);
        decide(mod, true);

        verify(rooms).commitItemProgress("ABC123", 1L, null);
        assertEquals(GamePhase.RESULT, game.getPhase());
        assertEquals(1, game.getResults().size());
        for (WebSocketSession s : List.of(pro, con, mod)) {
            List<JsonNode> got = messages(s);
            assertEquals(List.of("ROOM_STATE", "HAND", "CHAT_HISTORY"),
                    got.stream().map(m -> m.path("type").asText()).toList());
            JsonNode roomState = got.get(0).path("payload");
            assertEquals("RESULT", roomState.path("phase").asText());
            assertEquals(1, roomState.path("results").size());
            assertTrue(roomState.path("results").get(0).path("accepted").asBoolean());
            assertTrue(got.get(1).path("payload").isEmpty());
            assertTrue(got.get(2).path("payload").isEmpty());
        }
    }

    @Test
    void failedDatabaseWriteLeavesStateAndResultsUntouchedAndEmitsOnlyError() throws Exception {
        RoomGameState game = state(1);
        WebSocketSession pro = connect(11);
        WebSocketSession mod = connect(13);
        doThrow(new IllegalStateException("DB transaction failed"))
                .when(rooms).commitItemProgress("ABC123", 1L, null);
        decide(mod, false);

        assertEquals(GamePhase.MODERATOR_DECISION, game.getPhase());
        assertNotNull(game.getCurrentItem());
        assertTrue(game.getResults().isEmpty());
        assertEquals("ERROR", messages(mod).getFirst().path("type").asText());
        verify(pro, never()).sendMessage(any());
    }

    @Test
    void failedCommitCanBeRetriedAndPublishesExactlyOneResult() throws Exception {
        RoomGameState game = state(1);
        WebSocketSession mod = connect(13);
        doThrow(new IllegalStateException("commit failure")).doNothing()
                .when(rooms).commitItemProgress("ABC123", 1L, null);
        decide(mod, true);
        assertEquals(GamePhase.MODERATOR_DECISION, game.getPhase());
        decide(mod, true);
        assertEquals(GamePhase.RESULT, game.getPhase());
        assertEquals(1, game.getResults().size());
        verify(rooms, times(2)).commitItemProgress("ABC123", 1L, null);
        assertEquals(1, messages(mod).stream().filter(node ->
                "ROOM_STATE".equals(node.path("type").asText())).count());
    }

    @Test
    void duplicateDecisionInResultCannotPersistOrBroadcastAgain() throws Exception {
        RoomGameState game = state(1);
        WebSocketSession mod = connect(13);
        decide(mod, true);
        clearInvocations(mod);
        decide(mod, false);
        assertEquals(GamePhase.RESULT, game.getPhase());
        assertEquals(1, game.getResults().size());
        verify(rooms, times(1)).commitItemProgress("ABC123", 1L, null);
        assertEquals(List.of("ERROR"),
                messages(mod).stream().map(node -> node.path("type").asText()).toList());
    }

    @Test
    void guestsCannotAdvanceFinalDecision() throws Exception {
        RoomGameState game = state(1);
        WebSocketSession pro = connect(11);
        decide(pro, true);
        assertEquals(GamePhase.MODERATOR_DECISION, game.getPhase());
        assertTrue(game.getResults().isEmpty());
        verifyNoInteractions(rooms);
        assertEquals("ERROR", messages(pro).getFirst().path("type").asText());
    }

    @Test
    void nextItemUsesSameDatabaseCommitMethodWithoutEndingGame() throws Exception {
        RoomGameState game = state(2);
        WebSocketSession mod = connect(13);
        decide(mod, true);
        verify(rooms).commitItemProgress("ABC123", 1L, 2L);
        assertEquals(GamePhase.VOTING, game.getPhase());
        assertEquals("다음 물품", game.getCurrentItem().name());
        assertEquals(1, game.getResults().size());
        assertEquals("VOTING", messages(mod).getFirst().path("payload").path("phase").asText());
    }

    @Test
    void finishedRoomRejectsModeratorChatWithoutMoreEvents() throws Exception {
        state(1);
        WebSocketSession mod = connect(13);
        decide(mod, true);
        clearInvocations(mod);
        handler.handleTextMessage(mod, new TextMessage(
                "{\"type\":\"SEND_CHAT\",\"payload\":{\"text\":\"after result\"}}"));
        assertEquals("ERROR", messages(mod).getFirst().path("type").asText());
    }
    @Test
    void simultaneousDuplicateFinalDecisionsCommitOnlyOnce() throws Exception {
        RoomGameState game = state(1);
        WebSocketSession mod = connect(13);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                try { decide(mod, true); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            Future<?> second = pool.submit(() -> {
                try { decide(mod, true); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(GamePhase.RESULT, game.getPhase());
        assertEquals(1, game.getResults().size());
        verify(rooms).commitItemProgress("ABC123", 1L, null);
        assertEquals(1, messages(mod).stream().filter(m ->
                "ROOM_STATE".equals(m.path("type").asText())).count());
    }
}
