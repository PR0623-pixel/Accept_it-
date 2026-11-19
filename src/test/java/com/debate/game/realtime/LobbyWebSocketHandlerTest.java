package com.debate.game.realtime;

import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.service.RoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class LobbyWebSocketHandlerTest {
    RoomService rooms = mock(RoomService.class);
    LobbyStateReader reader = mock(LobbyStateReader.class);
    ObjectMapper json = new ObjectMapper();
    LobbyWebSocketHandler handler = new LobbyWebSocketHandler(rooms, reader, new LobbyRoomStateMapper(), json);

    LobbySnapshotResponse lobby(String code, String playerId) {
        return new LobbySnapshotResponse(code, playerId, "LOBBY",
                List.of(new LobbySnapshotResponse.LobbyPlayer(playerId, "참가자", "GUEST", true, true)),
                new LobbySnapshotResponse.LobbySettings(3, 300, 3, 120, 60));
    }

    WebSocketSession socket(String id, String roomCode, String playerId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/rooms/" + roomCode + "?playerId=" + playerId));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    List<JsonNode> messages(WebSocketSession session, int expected) throws Exception {
        ArgumentCaptor<TextMessage> cap = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(expected)).sendMessage(cap.capture());
        return cap.getAllValues().stream().map(m -> {
            try { return json.readTree(m.getPayload()); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).toList();
    }

    @Test
    void connectSendsStateThenEmptyHandThenEmptyChatHistory() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        WebSocketSession s1 = socket("sessionA", "ABC123", "11");
        handler.afterConnectionEstablished(s1);
        var got = messages(s1, 3);
        assertEquals(List.of("ROOM_STATE", "HAND", "CHAT_HISTORY"),
                got.stream().map(n -> n.get("type").asText()).toList());
        assertEquals("ABC123", got.get(0).get("payload").get("roomCode").asText());
        assertEquals(300, got.get(0).get("payload").get("settings").get("itemSeconds").asInt());
        assertEquals(0, got.get(1).get("payload").size());
        assertEquals(0, got.get(2).get("payload").size());
    }

    @Test
    void broadcastIsIsolatedToOwnRoom() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        when(reader.read("ZZZ999")).thenReturn(lobby("ZZZ999", "22"));
        WebSocketSession a = socket("a", "ABC123", "11");
        WebSocketSession b = socket("b", "ZZZ999", "22");
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);
        handler.broadcastRoomState("ABC123");
        assertEquals(4, messages(a, 4).size());
        assertEquals(3, messages(b, 3).size());
    }

    @Test
    void acceptsLobbyCommandsUsingConnectionIdentityNotClientPayloadId() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        WebSocketSession a = socket("a", "ABC123", "11");
        handler.afterConnectionEstablished(a);
        handler.handleTextMessage(a, new TextMessage("{\"type\":\"SET_ROLE\",\"payload\":{\"role\":\"MODERATOR\",\"playerId\":999}}"));
        handler.handleTextMessage(a, new TextMessage("{\"type\":\"SET_READY\",\"payload\":{\"ready\":true}}"));
        handler.handleTextMessage(a, new TextMessage("{\"type\":\"UPDATE_SETTINGS\",\"payload\":{\"maxItems\":4,\"itemSeconds\":480,\"cardsPerItem\":2}}"));
        verify(rooms).selectRole(11L, "MODERATOR");
        verify(rooms).setReady("ABC123", 11L, true);
        verify(rooms).updateSocketSettings("ABC123", 11L, 4, 480, 2);
        assertEquals(3, messages(a, 3).size()); // no direct success echo; after-commit broadcaster owns changes
    }

    @Test
    void invalidCommandsOnlySendErrorToRequester() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        WebSocketSession a = socket("a", "ABC123", "11");
        handler.afterConnectionEstablished(a);
        handler.handleTextMessage(a, new TextMessage("{\"type\":\"SET_READY\",\"payload\":{\"ready\":\"true\"}}"));
        var messages = messages(a, 4);
        assertEquals("ERROR", messages.get(3).get("type").asText());
        assertEquals("INVALID", messages.get(3).get("payload").get("code").asText());
        verifyNoInteractions(rooms);
    }

    @Test
    void hostLeaveNotifiesAndClosesRoomOnly() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        when(reader.read("ZZZ999")).thenReturn(lobby("ZZZ999", "22"));
        WebSocketSession a = socket("a", "ABC123", "11");
        WebSocketSession b = socket("b", "ZZZ999", "22");
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);
        handler.closeRoom("ABC123");
        assertEquals("ROOM_NOT_FOUND", messages(a, 4).get(3).get("payload").get("code").asText());
        verify(a).close();
        verify(b, never()).close();
    }

    @Test
    void nonexistentRoomSendsErrorAndCloses() throws Exception {
        when(reader.read("ABC123")).thenThrow(new com.debate.game.exception.InvalidRoomCodeException("not found"));
        WebSocketSession a = socket("a", "ABC123", "11");
        handler.afterConnectionEstablished(a);
        assertEquals("ROOM_NOT_FOUND", messages(a, 1).get(0).get("payload").get("code").asText());
        verify(a).close();
    }
}
