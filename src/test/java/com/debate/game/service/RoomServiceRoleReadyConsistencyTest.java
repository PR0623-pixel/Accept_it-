package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.entity.enums.Stance;
import com.debate.game.exception.RoleFullException;
import com.debate.game.exception.RoleRequiredException;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** R12: verify that SET_ROLE cannot leave stale Ready flags that imply game start. */
@ExtendWith(MockitoExtension.class)
class RoomServiceRoleReadyConsistencyTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock RoomCodeGenerator codes;

    RoomService service;
    Room room;
    Player host;
    Player firstGuest;
    Player secondGuest;

    @BeforeEach
    void setUp() {
        service = new RoomService(rooms, players, new NicknameValidator(), codes);
        room = new Room();
        room.setId(10L);
        room.setRoomCode("AB12CD");
        room.setStatus(RoomStatus.WAITING);
        host = participant(1L, LobbyRole.MODERATOR);
        firstGuest = participant(2L, LobbyRole.GUEST);
        secondGuest = participant(3L, LobbyRole.GUEST);
        room.setHostPlayer(host);
    }

    private Player participant(long id, LobbyRole role) {
        Player p = new Player();
        p.setId(id);
        p.setRoom(room);
        p.setRole(role);
        return p;
    }

    private void allowRoleChange(long playerId, Player... roster) {
        when(players.findRoomIdByPlayerId(playerId)).thenReturn(Optional.of(room.getId()));
        when(rooms.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(roster));
    }

    private void allReady() {
        host.setReady(true);
        firstGuest.setReady(true);
        secondGuest.setReady(true);
    }

    @Test
    void changingGuestToNoneClearsOnlyTheirReadyAndCurrentItemStance() {
        allReady();
        firstGuest.setStance(Stance.PRO);
        allowRoleChange(2L, host, firstGuest, secondGuest);

        service.selectRole(2L, "NONE");

        assertEquals(LobbyRole.NONE, firstGuest.getRole());
        assertNull(firstGuest.getStance());
        assertFalse(firstGuest.isReady());
        assertTrue(host.isReady());
        assertTrue(secondGuest.isReady());
        assertFalse(List.of(host, firstGuest, secondGuest).stream().allMatch(Player::isReady));
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void reselectingSameRoleAlsoClearsReadyAsFrontendSetRoleDoes() {
        firstGuest.setReady(true);
        allowRoleChange(2L, host, firstGuest, secondGuest);

        service.selectRole(2L, "GUEST");

        assertEquals(LobbyRole.GUEST, firstGuest.getRole());
        assertFalse(firstGuest.isReady());
        assertFalse(host.isReady());
        assertFalse(secondGuest.isReady());
    }

    @Test
    void fullModeratorSlotDoesNotModifyReadyRoleOrStance() {
        allReady();
        firstGuest.setStance(Stance.CON);
        allowRoleChange(2L, host, firstGuest, secondGuest);

        assertThrows(RoleFullException.class, () -> service.selectRole(2L, "MODERATOR"));

        assertEquals(LobbyRole.GUEST, firstGuest.getRole());
        assertTrue(firstGuest.isReady());
        assertEquals(Stance.CON, firstGuest.getStance());
        assertTrue(host.isReady());
        assertTrue(secondGuest.isReady());
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void soloPlayerCanReadyThenLoseReadyOnRoleChange() {
        when(rooms.findByRoomCodeForUpdate("AB12CD")).thenReturn(Optional.of(room));
        when(players.findRoomIdByPlayerId(1L)).thenReturn(Optional.of(10L));
        when(rooms.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(host));

        service.setReady("AB12CD", 1L, true);
        assertTrue(host.isReady());
        service.selectRole(1L, "NONE");
        assertEquals(LobbyRole.NONE, host.getRole());
        assertFalse(host.isReady());
        assertSame(host, room.getHostPlayer()); // Host permission is independent
        assertThrows(RoleRequiredException.class, () -> service.setReady("AB12CD", 1L, true));
        assertFalse(host.isReady());
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void readyCancellationDoesNotChangeRoleOrOtherMembers() {
        allReady();
        when(rooms.findByRoomCodeForUpdate("AB12CD")).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(host, firstGuest, secondGuest));

        service.setReady("AB12CD", 2L, false);

        assertFalse(firstGuest.isReady());
        assertEquals(LobbyRole.GUEST, firstGuest.getRole());
        assertTrue(host.isReady());
        assertTrue(secondGuest.isReady());
    }
}
