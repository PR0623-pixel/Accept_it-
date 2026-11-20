package com.debate.game.Ingame;

import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.realtime.IngameRoomStateMapper;
import com.debate.game.realtime.LobbyRoomStateMapper;
import com.debate.game.realtime.LobbyStateReader;
import com.debate.game.realtime.LobbyWebSocketHandler;
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

class GameTimerWebSocketTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private RoomGameState debate() {
        RoomGameState state = new RoomGameState("ABC123",
                List.of(new RoomStatePayload.ItemPayload(1, "물품", "설명")),
                List.of(101L), new RoomStatePayload.RoomSettingsPayload(1, 10, 2),
                List.of(
                        new RoomGameState.PlayerState(11L, "객원", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "반대", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "사회자", LobbyRole.MODERATOR, true, true)),
                3, 2);
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.CON, 1_000L);
        state.giveCards(11L, List.of(new CardPayload(1L, "A", "A", "A", false)));
        state.giveCards(12L, List.of(new CardPayload(2L, "B", "B", "B", false)));
        return state;
    }

    private WebSocketSession socket(String name, long playerId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(name);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/rooms/ABC123?playerId=" + playerId));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private List<JsonNode> captured(WebSocketSession socket, int count) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, times(count)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(msg -> {
            try {
                return mapper.readTree(msg.getPayload());
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }).toList();
    }

    @Test
    void speechTimeoutBroadcastsRoomStateOnceToConnectedGuests() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 1L, 2_000L);
        RoomGameStateStore store = new RoomGameStateStore();
        store.save(state);
        LobbyStateReader reader = mock(LobbyStateReader.class);
        when(reader.read("ABC123")).thenReturn(new LobbySnapshotResponse("ABC123", "13", "LOBBY",
                List.of(new LobbySnapshotResponse.LobbyPlayer("11", "객원", "GUEST", true, false)),
                new LobbySnapshotResponse.LobbySettings(1, 300, 2, 3, 2)));
        LobbyWebSocketHandler handler = new LobbyWebSocketHandler(
                mock(RoomService.class), reader, new LobbyRoomStateMapper(),
                new IngameRoomStateMapper(), store, mapper, mock(SituationCardService.class));
        WebSocketSession guest = socket("guest", 11L);
        handler.afterConnectionEstablished(guest);
        clearInvocations(guest);

        GameTimerService timer = new GameTimerService(store, handler);
        timer.pollTimers(5_000L);
        timer.pollTimers(5_000L);
        var messages = captured(guest, 1);
        assertEquals("ROOM_STATE", messages.get(0).path("type").asText());
        assertTrue(messages.get(0).path("payload").path("speech").isNull());
        assertEquals("DEBATE", messages.get(0).path("payload").path("phase").asText());
    }

    @Test
    void expiredSpeechRequestDoesNotMarkDatabaseCardUsedBeforePoll() throws Exception {
        RoomGameState state = debate(); // starts at 1_000; deadline 11_000 (already past)
        RoomGameStateStore store = new RoomGameStateStore();
        store.save(state);
        LobbyStateReader reader = mock(LobbyStateReader.class);
        when(reader.read("ABC123")).thenReturn(new LobbySnapshotResponse("ABC123", "13", "LOBBY",
                List.of(new LobbySnapshotResponse.LobbyPlayer("11", "객원", "GUEST", true, false)),
                new LobbySnapshotResponse.LobbySettings(1, 300, 2, 3, 2)));
        SituationCardService cards = mock(SituationCardService.class);
        LobbyWebSocketHandler handler = new LobbyWebSocketHandler(
                mock(RoomService.class), reader, new LobbyRoomStateMapper(),
                new IngameRoomStateMapper(), store, mapper, cards);
        WebSocketSession guest = socket("guest", 11L);
        handler.afterConnectionEstablished(guest);
        clearInvocations(guest);

        handler.handleMessage(guest, new TextMessage(
                "{\"type\":\"START_SPEECH\",\"payload\":{\"cardId\":1}}"));
        var messages = captured(guest, 1);
        assertEquals("ERROR", messages.get(0).path("type").asText());
        assertEquals("TIME_EXPIRED", messages.get(0).path("payload").path("code").asText());
        verifyNoInteractions(cards);
        assertNull(state.getSpeech());
        assertFalse(state.handFor(11L).get(0).used());
    }

    @Test
    void itemTimeoutRestoresPendingCardAndBroadcastsRoomAndPrivateHandOnce() throws Exception {
        RoomGameState state = debate();
        state.startSpeech(11L, 1L, 2_000L);
        state.requestRebuttal(12L, 2L, 3_000L);
        RoomGameStateStore store = new RoomGameStateStore();
        store.save(state);
        LobbyStateReader reader = mock(LobbyStateReader.class);
        when(reader.read("ABC123")).thenReturn(new LobbySnapshotResponse("ABC123", "13", "LOBBY",
                List.of(new LobbySnapshotResponse.LobbyPlayer("12", "반대", "GUEST", true, false)),
                new LobbySnapshotResponse.LobbySettings(1, 300, 2, 3, 2)));
        LobbyWebSocketHandler handler = new LobbyWebSocketHandler(
                mock(RoomService.class), reader, new LobbyRoomStateMapper(),
                new IngameRoomStateMapper(), store, mapper, mock(SituationCardService.class));
        WebSocketSession guest = socket("guest", 12L);
        handler.afterConnectionEstablished(guest);
        clearInvocations(guest);

        GameTimerService timer = new GameTimerService(store, handler);
        timer.pollTimers(11_000L);
        timer.pollTimers(11_000L);
        var messages = captured(guest, 2);
        assertEquals("ROOM_STATE", messages.get(0).path("type").asText());
        assertEquals("MODERATOR_DECISION", messages.get(0).path("payload").path("phase").asText());
        assertTrue(messages.get(0).path("payload").path("pendingRebuttal").isNull());
        assertEquals("HAND", messages.get(1).path("type").asText());
        assertFalse(messages.get(1).path("payload").get(0).path("used").asBoolean());
    }
}
