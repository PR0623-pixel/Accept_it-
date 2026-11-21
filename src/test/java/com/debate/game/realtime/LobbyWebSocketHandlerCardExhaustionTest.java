package com.debate.game.realtime;

import com.debate.game.Ingame.RoomGameState;
import com.debate.game.Ingame.RoomGameStateStore;
import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.dto.websocket.CardPayload;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LobbyWebSocketHandlerCardExhaustionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private RoomGameState debate(int proCount, int conCount) {
        RoomGameState state = new RoomGameState("ABC123",
                List.of(new RoomStatePayload.ItemPayload(1, "물품", "설명")),
                List.of(1L), new RoomStatePayload.RoomSettingsPayload(1, 300, 3),
                List.of(
                        new RoomGameState.PlayerState(11L, "PRO", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "CON", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "MOD", LobbyRole.MODERATOR, true, true)),
                20, 10);
        long now = System.currentTimeMillis();
        state.vote(11L, Stance.PRO, now);
        state.vote(12L, Stance.CON, now);
        state.giveCards(11L, java.util.stream.IntStream.range(0, proCount)
                .mapToObj(i -> new CardPayload(100L + i, "P" + i, "P", "P", false)).toList());
        state.giveCards(12L, java.util.stream.IntStream.range(0, conCount)
                .mapToObj(i -> new CardPayload(200L + i, "C" + i, "C", "C", false)).toList());
        return state;
    }

    private WebSocketSession socket(String id, long playerId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/rooms/ABC123?playerId=" + playerId));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private LobbyWebSocketHandler handler(RoomGameState state, WebSocketSession... sessions) throws Exception {
        RoomGameStateStore store = new RoomGameStateStore();
        store.save(state);
        LobbyStateReader reader = mock(LobbyStateReader.class);
        when(reader.read("ABC123")).thenReturn(new LobbySnapshotResponse("ABC123", "13", "LOBBY",
                List.of(
                        new LobbySnapshotResponse.LobbyPlayer("11", "PRO", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("12", "CON", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("13", "MOD", "MODERATOR", true, true)),
                new LobbySnapshotResponse.LobbySettings(1, 300, 3, 20, 10)));
        LobbyWebSocketHandler handler = new LobbyWebSocketHandler(mock(RoomService.class),
                reader, new LobbyRoomStateMapper(), new IngameRoomStateMapper(),
                store, mapper, mock(SituationCardService.class));
        for (WebSocketSession session : sessions) {
            handler.afterConnectionEstablished(session);
            clearInvocations(session);
        }
        return handler;
    }

    private List<JsonNode> captured(WebSocketSession session, int count) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(count)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(message -> {
            try {
                return mapper.readTree(message.getPayload());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
    }

    @Test
    void finishingLastSpeechBroadcastsDecisionAndPrivateHandsOnlyOnce() throws Exception {
        RoomGameState state = debate(1, 2);
        state.startSpeech(11L, 100L, System.currentTimeMillis());
        WebSocketSession pro = socket("pro", 11L);
        WebSocketSession con = socket("con", 12L);
        WebSocketSession moderator = socket("mod", 13L);
        LobbyWebSocketHandler handler = handler(state, pro, con, moderator);

        handler.handleTextMessage(pro, new TextMessage("{\"type\":\"END_SPEECH\",\"payload\":{}}"));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        for (WebSocketSession session : List.of(pro, con, moderator)) {
            List<JsonNode> messages = captured(session, 2);
            assertEquals("ROOM_STATE", messages.get(0).path("type").asText());
            assertEquals("MODERATOR_DECISION", messages.get(0).path("payload").path("phase").asText());
            assertEquals("HAND", messages.get(1).path("type").asText());
        }
        JsonNode proHand = captured(pro, 2).get(1).path("payload");
        JsonNode conHand = captured(con, 2).get(1).path("payload");
        assertEquals(100L, proHand.get(0).path("cardId").asLong());
        assertEquals(200L, conHand.get(0).path("cardId").asLong());
        assertTrue(proHand.get(0).path("used").asBoolean());
        assertFalse(conHand.get(0).path("used").asBoolean());

        handler.handleTextMessage(pro, new TextMessage("{\"type\":\"END_SPEECH\",\"payload\":{}}"));
        // The invalid duplicate generates only a private ERROR, no duplicate room broadcast.
        assertEquals("ERROR", captured(pro, 3).get(2).path("type").asText());
        verify(con, times(2)).sendMessage(any(TextMessage.class));
        verify(moderator, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void deniedRebuttalRestoresCardAndRefreshesRequesterHandPrivately() throws Exception {
        RoomGameState state = debate(2, 1);
        state.startSpeech(11L, 100L, System.currentTimeMillis());
        state.requestRebuttal(12L, 200L, System.currentTimeMillis());
        assertTrue(state.handFor(12L).get(0).used());
        WebSocketSession pro = socket("pro", 11L);
        WebSocketSession con = socket("con", 12L);
        WebSocketSession moderator = socket("mod", 13L);
        LobbyWebSocketHandler handler = handler(state, pro, con, moderator);

        handler.handleTextMessage(moderator,
                new TextMessage("{\"type\":\"DECIDE_REBUTTAL\",\"payload\":{\"approve\":false}}"));
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertFalse(state.handFor(12L).get(0).used());
        List<JsonNode> conMessages = captured(con, 2);
        assertEquals("HAND", conMessages.get(0).path("type").asText());
        assertFalse(conMessages.get(0).path("payload").get(0).path("used").asBoolean());
        assertEquals("ROOM_STATE", conMessages.get(1).path("type").asText());
        assertEquals("DEBATE", conMessages.get(1).path("payload").path("phase").asText());
        assertEquals("ROOM_STATE", captured(pro, 1).get(0).path("type").asText());
        assertEquals("ROOM_STATE", captured(moderator, 1).get(0).path("type").asText());
    }

    @Test
    void approvedLastRebuttalThenEndBroadcastsDecision() throws Exception {
        RoomGameState state = debate(2, 1);
        state.startSpeech(11L, 100L, System.currentTimeMillis());
        state.requestRebuttal(12L, 200L, System.currentTimeMillis());
        WebSocketSession con = socket("con", 12L);
        WebSocketSession mod = socket("mod", 13L);
        LobbyWebSocketHandler handler = handler(state, con, mod);

        handler.handleTextMessage(mod,
                new TextMessage("{\"type\":\"DECIDE_REBUTTAL\",\"payload\":{\"approve\":true}}"));
        assertEquals(GamePhase.DEBATE, state.getPhase());
        assertEquals("REBUTTAL", state.getSpeech().kind());
        clearInvocations(con, mod);

        handler.handleTextMessage(con, new TextMessage("{\"type\":\"END_SPEECH\",\"payload\":{}}"));
        assertEquals(GamePhase.MODERATOR_DECISION, state.getPhase());
        assertEquals("MODERATOR_DECISION", captured(con, 2).get(0).path("payload").path("phase").asText());
        assertTrue(captured(con, 2).get(1).path("payload").get(0).path("used").asBoolean());
        assertEquals("HAND", captured(mod, 2).get(1).path("type").asText());
    }
}
