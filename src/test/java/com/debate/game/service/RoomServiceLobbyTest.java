package com.debate.game.service;

import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceLobbyTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock NicknameValidator validator;
    @Mock RoomCodeGenerator generator;
    RoomService service;

    @BeforeEach void setup() { service = new RoomService(rooms, players, validator, generator); }

    @Test void returnsLobbySnapshotWithHostAndPlayers() {
        Room room = new Room(); room.setRoomCode("ABC123"); room.setStatus(RoomStatus.WAITING);
        room.setMaxCardCount(3); room.setTurnTimeLimit(5);
        room.setMaxItems(4); room.setSpeechSeconds(150); room.setRebuttalSeconds(75);
        Player host = new Player(); host.setId(1L); host.setPlayerName("Host"); host.setRoom(room);
        Player guest = new Player(); guest.setId(2L); guest.setPlayerName("Guest"); guest.setRoom(room); guest.setRole(LobbyRole.GUEST);
        room.setHostPlayer(host);
        when(rooms.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        when(players.findByRoom(room)).thenReturn(List.of(guest, host));
        LobbySnapshotResponse result = service.getLobby("abc123");
        assertEquals("ABC123", result.roomCode());
        assertEquals("1", result.hostPlayerId());
        assertEquals("LOBBY", result.phase());
        assertEquals(2, result.players().size());
        assertEquals("1", result.players().get(0).id());
        assertTrue(result.players().get(0).isHost());
        assertEquals("GUEST", result.players().get(1).role());
        assertEquals("NONE", result.players().get(0).role());
        assertEquals(300, result.settings().itemSeconds());
        assertEquals(3, result.settings().cardsPerItem());
        assertEquals(4, result.settings().maxItems());
        assertEquals(150, result.settings().speechSeconds());
        assertEquals(75, result.settings().rebuttalSeconds());
    }

    @Test void missingRoomReturnsRoomNotFoundException() {
        when(rooms.findByRoomCode("ABC123")).thenReturn(Optional.empty());
        assertThrows(InvalidRoomCodeException.class, () -> service.getLobby("ABC123"));
        verifyNoInteractions(players);
    }

    @Test void invalidCodeDoesNotQueryDatabase() {
        assertThrows(InvalidRoomCodeException.class, () -> service.getLobby("BAD!"));
        verifyNoInteractions(rooms, players);
    }
}
