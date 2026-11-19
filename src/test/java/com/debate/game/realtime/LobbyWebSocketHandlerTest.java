package com.debate.game.realtime;

import com.debate.game.Ingame.RoomGameState;
import com.debate.game.Ingame.RoomGameStateStore;
import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.service.RoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    RoomGameStateStore gameStates = new RoomGameStateStore();
    ObjectMapper json = new ObjectMapper();
    LobbyWebSocketHandler handler = new LobbyWebSocketHandler(
            rooms, reader, new LobbyRoomStateMapper(), new IngameRoomStateMapper(), gameStates, json);

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
        return cap.getAllValues().stream().map(message -> {
            try {
                return json.readTree(message.getPayload());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
    }

    private RoomGameState gameState() {
        return new RoomGameState("ABC123",
                new com.debate.game.dto.websocket.RoomStatePayload.ItemPayload(1, "물품", "설명"),
                new com.debate.game.dto.websocket.RoomStatePayload.RoomSettingsPayload(3, 300, 3),
                List.of(
                        new RoomGameState.PlayerState(11L, "객원1", LobbyRole.GUEST, true, true),
                        new RoomGameState.PlayerState(12L, "객원2", LobbyRole.GUEST, true, false),
                        new RoomGameState.PlayerState(13L, "사회자", LobbyRole.MODERATOR, true, false)));
    }

    @Test
    void connectSendsStateThenEmptyHandThenEmptyChatHistory() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        WebSocketSession session = socket("sessionA", "ABC123", "11");
        handler.afterConnectionEstablished(session);
        var got = messages(session, 3);
        assertEquals(List.of("ROOM_STATE", "HAND", "CHAT_HISTORY"),
                got.stream().map(node -> node.get("type").asText()).toList());
        assertEquals("ABC123", got.get(0).get("payload").get("roomCode").asText());
        assertEquals(300, got.get(0).get("payload").get("settings").get("itemSeconds").asInt());
        assertEquals(0, got.get(1).get("payload").size());
        assertEquals(0, got.get(2).get("payload").size());
    }

    @Test
    void reconnectUsesInMemoryGameSnapshotAndPrivateHandAndChat() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        RoomGameState state = gameState();
        state.getHands().get(11L).add(new com.debate.game.dto.websocket.CardPayload(7, "키워드", "제목", "설명", false));
        state.getChatHistory().add(new com.debate.game.dto.websocket.ChatMessagePayload(
                "1", "SPEECH", "11", "객원1", "PRO", "내용", "키워드", 100L));
        gameStates.save(state);

        WebSocketSession session = socket("reconnect", "ABC123", "11");
        handler.afterConnectionEstablished(session);
        var got = messages(session, 3);
        assertEquals("VOTING", got.get(0).get("payload").get("phase").asText());
        assertEquals(7, got.get(1).get("payload").get(0).get("cardId").asInt());
        assertEquals("내용", got.get(2).get("payload").get(0).get("text").asText());
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
    void voteBroadcastsPersonalizedStateAndMasksOtherGuestVote() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        gameStates.save(gameState());
        WebSocketSession session = socket("guest", "ABC123", "11");
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"VOTE\",\"payload\":{\"stance\":\"PRO\"}}"));

        var got = messages(session, 4);
        assertEquals("ROOM_STATE", got.get(3).get("type").asText());
        JsonNode players = got.get(3).get("payload").get("players");
        assertEquals("PRO", players.get(0).get("stance").asText());
        assertTrue(players.get(0).get("hasVoted").asBoolean());
        assertTrue(players.get(1).get("stance").isNull());
        assertFalse(players.get(1).get("hasVoted").asBoolean());
        verifyNoInteractions(rooms);
    }

    @Test
    void secondVoteAdvancesToDebateWhenStancesDiffer() {
        RoomGameState state = gameState();
        state.vote(11L, Stance.PRO, 1_000L);
        assertEquals("VOTING", state.getPhase().name());
        state.vote(12L, Stance.CON, 1_000L);
        assertEquals("DEBATE", state.getPhase().name());
        assertEquals(301_000L, state.getItemEndsAt());
    }

    @Test
    void acceptsLobbyCommandsUsingConnectionIdentityNotClientPayloadId() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        WebSocketSession session = socket("a", "ABC123", "11");
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"SET_ROLE\",\"payload\":{\"role\":\"MODERATOR\",\"playerId\":999}}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"SET_READY\",\"payload\":{\"ready\":true}}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"UPDATE_SETTINGS\",\"payload\":{\"maxItems\":4,\"itemSeconds\":480,\"cardsPerItem\":2}}"));
        verify(rooms).selectRole(11L, "MODERATOR");
        verify(rooms).setReady("ABC123", 11L, true);
        verify(rooms).updateSocketSettings("ABC123", 11L, 4, 480, 2);
        assertEquals(3, messages(session, 3).size());
    }

    @Test
    void invalidCommandsOnlySendErrorToRequester() throws Exception {
        when(reader.read("ABC123")).thenReturn(lobby("ABC123", "11"));
        WebSocketSession session = socket("a", "ABC123", "11");
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"SET_READY\",\"payload\":{\"ready\":\"true\"}}"));
        var got = messages(session, 4);
        assertEquals("ERROR", got.get(3).get("type").asText());
        assertEquals("INVALID", got.get(3).get("payload").get("code").asText());
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
        WebSocketSession session = socket("a", "ABC123", "11");
        handler.afterConnectionEstablished(session);
        assertEquals("ROOM_NOT_FOUND", messages(session, 1).get(0).get("payload").get("code").asText());
        verify(session).close();
    }
}
