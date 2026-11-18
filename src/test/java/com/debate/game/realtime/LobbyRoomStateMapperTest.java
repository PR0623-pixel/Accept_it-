package com.debate.game.realtime;

import com.debate.game.dto.LobbySnapshotResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LobbyRoomStateMapperTest {
    @Test
    void producesExactLobbyFieldsExpectedByFrontend() {
        var mapper = new LobbyRoomStateMapper(Clock.fixed(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC));
        var snapshot = new LobbySnapshotResponse("ABC123", "11", "LOBBY",
                List.of(new LobbySnapshotResponse.LobbyPlayer("11", "방장", "MODERATOR", true, true),
                        new LobbySnapshotResponse.LobbyPlayer("12", "손님", "NONE", false, false)),
                new LobbySnapshotResponse.LobbySettings(4, 480, 2, 120, 60));
        Map<String, Object> state = mapper.map(snapshot);
        assertEquals("ABC123", state.get("roomCode"));
        assertEquals(1700000000000L, state.get("serverTime"));
        assertEquals("LOBBY", state.get("phase"));
        assertEquals(12, state.size());
        assertEquals(Map.of("maxItems", 4, "itemSeconds", 480, "cardsPerItem", 2), state.get("settings"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        assertEquals(2, players.size());
        assertEquals(7, players.get(0).size());
        assertNull(players.get(0).get("stance"));
        assertFalse((Boolean) players.get(0).get("hasVoted"));
        assertTrue((Boolean) players.get(0).get("isHost"));
        for (String key : List.of("currentItem", "itemEndsAt", "speech", "lastSpeakerId",
                "pendingRebuttal", "effect")) assertNull(state.get(key));
        assertEquals(List.of(), state.get("results"));
    }
}
