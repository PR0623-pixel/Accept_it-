package com.debate.game.realtime;

import com.debate.game.Ingame.RoomGameState;
import com.debate.game.Ingame.RoomGameStateStore;
import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.service.RoomService;
import com.debate.game.service.SituationCardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LobbyWebSocketHandlerDebateFinishTest {

    @Test
    void endDebateBroadcastsUpdatedStateAndPrivateHandsOnlyOnce() throws Exception {
        RoomGameState state = new RoomGameState("ABC123",
                List.of(new RoomStatePayload.ItemPayload(1, "물품", "설명")),
                List.of(1L),
                new RoomStatePayload.RoomSettingsPayload(1, 300, 3),
                List.of(
                        new RoomGameState.PlayerState(11L, "PRO", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(12L, "CON", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "MOD", LobbyRole.MODERATOR, true, true)));
        state.vote(11L, Stance.PRO, 1_000L);
        state.vote(12L, Stance.CON, 1_000L);
        state.giveCards(11L, List.of(new CardPayload(1L, "A", "A", "A", false)));
        state.giveCards(12L, List.of(new CardPayload(2L, "B", "B", "B", false)));
        state.startSpeech(11L, 1L, 2_000L);
        state.requestRebuttal(12L, 2L, 3_000L);

        RoomGameStateStore store = new RoomGameStateStore();
        store.save(state);
        LobbyStateReader reader = mock(LobbyStateReader.class);
        when(reader.read("ABC123")).thenReturn(new LobbySnapshotResponse(
                "ABC123", "13", "LOBBY",
                List.of(
                        new LobbySnapshotResponse.LobbyPlayer("11", "PRO", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("12", "CON", "GUEST", true, false),
                        new LobbySnapshotResponse.LobbyPlayer("13", "MOD", "MODERATOR", true, true)),
                new LobbySnapshotResponse.LobbySettings(1, 300, 3, 120, 60)));
        WebSocketSession mod = socket("mod", 13L);
        WebSocketSession guest = socket("guest", 12L);
        ObjectMapper json = new ObjectMapper();
        LobbyWebSocketHandler handler = new LobbyWebSocketHandler(
                mock(RoomService.class), reader, new LobbyRoomStateMapper(),
                new IngameRoomStateMapper(), store, json, mock(SituationCardService.class));
        handler.afterConnectionEstablished(mod);
        handler.afterConnectionEstablished(guest);
        clearInvocations(mod, guest);

        handler.handleTextMessage(mod, new TextMessage("{\"type\":\"END_DEBATE\",\"payload\":{}}"));
        assertEquals(com.debate.game.Ingame.enums.GamePhase.MODERATOR_DECISION, state.getPhase());
        assertFalse(state.handFor(12L).get(0).used());

        var guestCaptor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(guest, times(2)).sendMessage(guestCaptor.capture());
        var guestMessages = guestCaptor.getAllValues();
        assertEquals("ROOM_STATE", json.readTree(guestMessages.get(0).getPayload()).get("type").asText());
        assertEquals("MODERATOR_DECISION", json.readTree(guestMessages.get(0).getPayload()).path("payload").path("phase").asText());
        assertEquals("HAND", json.readTree(guestMessages.get(1).getPayload()).path("type").asText());
        assertFalse(json.readTree(guestMessages.get(1).getPayload()).path("payload").get(0).path("used").asBoolean());
        verify(mod, times(2)).sendMessage(any(TextMessage.class));

        handler.handleTextMessage(mod, new TextMessage("{\"type\":\"END_DEBATE\",\"payload\":{}}"));
        verify(mod, times(2)).sendMessage(any(TextMessage.class));
        verify(guest, times(2)).sendMessage(any(TextMessage.class));
    }

    private WebSocketSession socket(String name, Long playerId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(name);
        when(socket.getUri()).thenReturn(URI.create("ws://localhost/ws/rooms/ABC123?playerId=" + playerId));
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }
}
