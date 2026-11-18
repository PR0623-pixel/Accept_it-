package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.entity.enums.Stance;
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
class RoomServiceRoleTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock RoomCodeGenerator codes;

    RoomService service;
    Room room;
    Player guest;

    @BeforeEach
    void setUp() {
        service = new RoomService(rooms, players, new NicknameValidator(), codes);
        room = new Room();
        room.setId(10L);
        room.setStatus(RoomStatus.WAITING);
        guest = new Player();
        guest.setId(12L);
        guest.setRoom(room);
    }

    @Test
    void guestCanChooseLobbyRoleWithoutBeingFixedToProOrCon() {
        when(players.findById(12L)).thenReturn(Optional.of(guest));
        service.selectRole(12L, " guest ");

        assertEquals(LobbyRole.GUEST, guest.getRole());
        assertNull(guest.getStance());
        assertFalse(guest.isReady());
        verify(players, never()).findByRoom(any()); // role limits handled in R10
    }

    @Test
    void hostPermissionIsIndependentOfLobbyRole() {
        room.setHostPlayer(guest);
        when(players.findById(12L)).thenReturn(Optional.of(guest));
        service.selectRole(12L, "moderator");

        assertSame(guest, room.getHostPlayer());
        assertEquals(LobbyRole.MODERATOR, guest.getRole());
    }

    @Test
    void noneClearsLobbyRoleAndReadiness() {
        guest.setRole(LobbyRole.GUEST);
        guest.setStance(Stance.PRO); // defensive: stale data must not survive role change
        guest.setReady(true);
        when(players.findById(12L)).thenReturn(Optional.of(guest));

        service.selectRole(12L, "NONE");

        assertEquals(LobbyRole.NONE, guest.getRole());
        assertNull(guest.getStance());
        assertFalse(guest.isReady());
    }

    @Test
    void changingRoleClearsReadyAndStaleStance() {
        guest.setRole(LobbyRole.GUEST);
        guest.setReady(true);
        guest.setStance(Stance.CON);
        when(players.findById(12L)).thenReturn(Optional.of(guest));

        service.selectRole(12L, "MODERATOR");

        assertEquals(LobbyRole.MODERATOR, guest.getRole());
        assertNull(guest.getStance());
        assertFalse(guest.isReady());
    }

    @Test
    void proConAreNotLobbyRoles() {
        when(players.findById(12L)).thenReturn(Optional.of(guest));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, "PRO"));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, "CON"));
        assertEquals(LobbyRole.NONE, guest.getRole());
    }

    @Test
    void rejectsInvalidAndNullRole() {
        when(players.findById(12L)).thenReturn(Optional.of(guest));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, null));
    }

    @Test
    void cannotChangeRoleAfterRoomStarts() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(players.findById(12L)).thenReturn(Optional.of(guest));
        assertThrows(IllegalStateException.class, () -> service.selectRole(12L, "GUEST"));
        assertEquals(LobbyRole.NONE, guest.getRole());
    }

    @Test
    void missingPlayerOrDetachedPlayerCannotChoose() {
        when(players.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(99L, "GUEST"));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(null, "GUEST"));
        when(players.findById(12L)).thenReturn(Optional.of(guest));
        guest.setRoom(null);
        assertThrows(IllegalStateException.class, () -> service.selectRole(12L, "GUEST"));
    }
}
