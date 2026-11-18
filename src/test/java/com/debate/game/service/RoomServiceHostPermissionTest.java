package com.debate.game.service;

import com.debate.game.dto.RoomSettingsUpdateRequest;
import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.NotHostException;
import com.debate.game.exception.PlayerNotInRoomException;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** R15 — settings authority belongs to the room Host, not the MODERATOR lobby role. */
@ExtendWith(MockitoExtension.class)
class RoomServiceHostPermissionTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock NicknameValidator nicknameValidator;
    @Mock RoomCodeGenerator roomCodeGenerator;

    RoomService service;
    Room room;
    Player host;
    Player guest;

    private RoomSettingsUpdateRequest settings(String playerId) {
        return new RoomSettingsUpdateRequest(playerId, 4, 150, 75, 480, 5);
    }

    @BeforeEach
    void setup() {
        service = new RoomService(rooms, players, nicknameValidator, roomCodeGenerator);
        room = new Room();
        room.setId(3L);
        room.setRoomCode("ABC123");
        host = new Player();
        host.setId(11L);
        host.setRoom(room);
        host.setRole(LobbyRole.GUEST);
        host.setReady(true);
        guest = new Player();
        guest.setId(12L);
        guest.setRoom(room);
        guest.setRole(LobbyRole.MODERATOR);
        guest.setReady(true);
        room.setHostPlayer(host);
    }

    @Test
    void hostMayChangeSettingsEvenIfTheirLobbyRoleIsGuest() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findRoomIdByPlayerId(11L)).thenReturn(Optional.of(3L));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(host, guest));

        service.updateSettings("abc123", 11L, settings("11"));

        assertEquals(4, room.getMaxCardCount());
        assertEquals(150, room.getSpeechSeconds());
        assertEquals(75, room.getRebuttalSeconds());
        assertEquals(8, room.getTurnTimeLimit());
        assertEquals(5, room.getMaxItems());
        assertFalse(host.isReady());
        assertFalse(guest.isReady());
        // The room lock must precede reads of caller membership and members' Ready.
        InOrder order = inOrder(rooms, players);
        order.verify(rooms).findByRoomCodeForUpdate("ABC123");
        order.verify(players).findRoomIdByPlayerId(11L);
        order.verify(players).findByRoomForUpdate(room);
    }

    @Test
    void moderatorIsNotHostAndCannotChangeSettings() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findRoomIdByPlayerId(12L)).thenReturn(Optional.of(3L));
        int before = room.getMaxItems();

        assertThrows(NotHostException.class,
                () -> service.updateSettings("ABC123", 12L, settings("12")));

        assertEquals(before, room.getMaxItems());
        assertTrue(host.isReady());
        assertTrue(guest.isReady());
        verify(players, never()).findByRoomForUpdate(any());
        verify(rooms, never()).save(any());
    }

    @Test
    void playerFromAnotherRoomGetsPlayerNotInRoom() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findRoomIdByPlayerId(12L)).thenReturn(Optional.of(999L));
        assertThrows(PlayerNotInRoomException.class,
                () -> service.updateSettings("ABC123", 12L, settings("12")));
        assertTrue(host.isReady());
        assertTrue(guest.isReady());
        verify(players, never()).findByRoomForUpdate(any());
    }

    @Test
    void unknownPlayerGetsPlayerNotInRoom() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findRoomIdByPlayerId(999L)).thenReturn(Optional.empty());
        assertThrows(PlayerNotInRoomException.class,
                () -> service.updateSettings("ABC123", 999L, settings("999")));
        verify(players, never()).findByRoomForUpdate(any());
    }

    @Test
    void memberCannotChangeSettingsIfRoomHasNoHost() {
        room.setHostPlayer(null);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findRoomIdByPlayerId(12L)).thenReturn(Optional.of(3L));
        assertThrows(NotHostException.class,
                () -> service.updateSettings("ABC123", 12L, settings("12")));
        verify(players, never()).findByRoomForUpdate(any());
    }

    @Test
    void startedRoomRejectsHostBeforeAnyMemberLookup() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(AlreadyStartedException.class,
                () -> service.updateSettings("ABC123", 11L, settings("11")));
        verifyNoInteractions(players);
    }
}
