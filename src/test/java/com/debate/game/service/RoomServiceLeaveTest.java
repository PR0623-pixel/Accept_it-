package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.exception.*;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceLeaveTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock NicknameValidator validator;
    @Mock RoomCodeGenerator generator;
    RoomService service;
    Room room;
    Player host, guest;

    @BeforeEach void setup() {
        service = new RoomService(rooms, players, validator, generator);
        room = new Room(); room.setId(10L); room.setRoomCode("ABC123"); room.setStatus(RoomStatus.WAITING);
        host = new Player(); host.setId(1L); host.setRoom(room);
        guest = new Player(); guest.setId(2L); guest.setRoom(room);
        room.setHostPlayer(host);
    }

    @Test void guestLeavesAndCanFreeSlot() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findById(2L)).thenReturn(Optional.of(guest));
        service.leaveRoom("abc123", 2L);
        verify(players).delete(guest);
        verify(rooms).findByRoomCodeForUpdate("ABC123");
    }

    @Test void hostCannotLeaveUntilR08() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findById(1L)).thenReturn(Optional.of(host));
        assertThrows(HostLeavePendingException.class, () -> service.leaveRoom("ABC123", 1L));
        verify(players, never()).delete(any(Player.class));
    }

    @Test void cannotRemovePlayerFromAnotherRoom() {
        Room other = new Room(); other.setId(11L); guest.setRoom(other);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findById(2L)).thenReturn(Optional.of(guest));
        assertThrows(PlayerNotInRoomException.class, () -> service.leaveRoom("ABC123", 2L));
        verify(players, never()).delete(any(Player.class));
    }

    @Test void startedRoomCannotLeave() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(AlreadyStartedException.class, () -> service.leaveRoom("ABC123", 2L));
        verifyNoInteractions(players);
    }

    @Test void missingRoom() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.empty());
        assertThrows(InvalidRoomCodeException.class, () -> service.leaveRoom("ABC123", 2L));
    }

    @Test void missingPlayer() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findById(99L)).thenReturn(Optional.empty());
        assertThrows(PlayerNotInRoomException.class, () -> service.leaveRoom("ABC123", 99L));
    }
}
