package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.entity.enums.Stance;
import com.debate.game.exception.RoleFullException;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        guest = newPlayer(12L, LobbyRole.NONE);
    }

    private Player newPlayer(long id, LobbyRole role) {
        Player p = new Player();
        p.setId(id);
        p.setRoom(room);
        p.setRole(role);
        return p;
    }

    private void assignRoster(Player... others) {
        when(players.findRoomIdByPlayerId(12L)).thenReturn(Optional.of(10L));
        when(rooms.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        List<Player> roster = new ArrayList<>();
        roster.add(guest);
        roster.addAll(Arrays.asList(others));
        when(players.findByRoomForUpdate(room)).thenReturn(roster);
    }

    @Test
    void guestCanChooseLobbyRoleWithoutBeingFixedToProOrCon() {
        assignRoster();
        service.selectRole(12L, " guest ");
        assertEquals(LobbyRole.GUEST, guest.getRole());
        assertNull(guest.getStance());
        assertFalse(guest.isReady());
    }

    @Test
    void hostPermissionIsIndependentOfLobbyRole() {
        room.setHostPlayer(guest);
        assignRoster();
        service.selectRole(12L, "moderator");
        assertSame(guest, room.getHostPlayer());
        assertEquals(LobbyRole.MODERATOR, guest.getRole());
    }

    @Test
    void noneClearsLobbyRoleAndReadinessEvenWithOccupiedSlots() {
        guest.setRole(LobbyRole.MODERATOR);
        guest.setStance(Stance.PRO);
        guest.setReady(true);
        assignRoster(newPlayer(13L, LobbyRole.MODERATOR));
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
        assignRoster();
        service.selectRole(12L, "MODERATOR");
        assertEquals(LobbyRole.MODERATOR, guest.getRole());
        assertNull(guest.getStance());
        assertFalse(guest.isReady());
    }

    @Test
    void secondModeratorIsRejectedWithoutMutatingPlayer() {
        Player other = newPlayer(13L, LobbyRole.MODERATOR);
        guest.setReady(true);
        guest.setStance(Stance.PRO);
        assignRoster(other);
        assertThrows(RoleFullException.class, () -> service.selectRole(12L, "MODERATOR"));
        assertEquals(LobbyRole.NONE, guest.getRole());
        assertTrue(guest.isReady());
        assertEquals(Stance.PRO, guest.getStance());
    }

    @Test
    void secondGuestCanBeSelected() {
        assignRoster(newPlayer(13L, LobbyRole.GUEST), newPlayer(14L, LobbyRole.MODERATOR));
        service.selectRole(12L, "GUEST");
        assertEquals(LobbyRole.GUEST, guest.getRole());
    }

    @Test
    void thirdGuestIsRejected() {
        assignRoster(newPlayer(13L, LobbyRole.GUEST), newPlayer(14L, LobbyRole.GUEST));
        assertThrows(RoleFullException.class, () -> service.selectRole(12L, "GUEST"));
        assertEquals(LobbyRole.NONE, guest.getRole());
    }

    @Test
    void reselectSameRoleDoesNotCountSelfAndResetsReady() {
        guest.setRole(LobbyRole.MODERATOR);
        guest.setReady(true);
        assignRoster(newPlayer(13L, LobbyRole.GUEST));
        service.selectRole(12L, "MODERATOR");
        assertEquals(LobbyRole.MODERATOR, guest.getRole());
        assertFalse(guest.isReady());
    }

    @Test
    void switchingBetweenRolesFreesFormerSlot() {
        guest.setRole(LobbyRole.MODERATOR);
        assignRoster(newPlayer(13L, LobbyRole.GUEST));
        service.selectRole(12L, "GUEST");
        assertEquals(LobbyRole.GUEST, guest.getRole());
    }

    @Test
    void mustLockRoomBeforeReadingRoleRoster() {
        assignRoster();
        service.selectRole(12L, "GUEST");
        InOrder sequence = inOrder(players, rooms);
        sequence.verify(players).findRoomIdByPlayerId(12L);
        sequence.verify(rooms).findByIdForUpdate(10L);
        sequence.verify(players).findByRoomForUpdate(room);
    }

    @Test
    void proConAreNotLobbyRoles() {
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, "PRO"));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, "CON"));
        verifyNoInteractions(players, rooms);
    }

    @Test
    void rejectsInvalidAndNullRole() {
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, null));
    }

    @Test
    void cannotChangeRoleAfterRoomStarts() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(players.findRoomIdByPlayerId(12L)).thenReturn(Optional.of(10L));
        when(rooms.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        assertThrows(IllegalStateException.class, () -> service.selectRole(12L, "GUEST"));
        verify(players, never()).findByRoomForUpdate(any());
        assertEquals(LobbyRole.NONE, guest.getRole());
    }

    @Test
    void missingAndInvalidPlayerCannotChoose() {
        when(players.findRoomIdByPlayerId(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(99L, "GUEST"));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(null, "GUEST"));
    }

    @Test
    void playerWhoLeftWhileWaitingForLockCannotChoose() {
        when(players.findRoomIdByPlayerId(12L)).thenReturn(Optional.of(10L));
        when(rooms.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(newPlayer(13L, LobbyRole.NONE)));
        assertThrows(IllegalArgumentException.class, () -> service.selectRole(12L, "GUEST"));
        assertEquals(LobbyRole.NONE, guest.getRole());
    }
}
