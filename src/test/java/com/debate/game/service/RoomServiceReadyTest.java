package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.exception.NotEnoughPlayersException;
import com.debate.game.exception.PlayerNotInRoomException;
import com.debate.game.exception.RoleRequiredException;
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

@ExtendWith(MockitoExtension.class)
class RoomServiceReadyTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock RoomCodeGenerator codes;

    RoomService service;
    Room room;
    Player actor;
    Player guest;
    Player moderator;

    @BeforeEach
    void setUp() {
        service = new RoomService(rooms, players, new NicknameValidator(), codes);
        room = new Room();
        room.setId(10L);
        room.setRoomCode("AB12CD");
        room.setStatus(RoomStatus.WAITING);
        actor = player(12L, LobbyRole.GUEST);
        guest = player(13L, LobbyRole.GUEST);
        moderator = player(14L, LobbyRole.MODERATOR);
    }

    private Player player(long id, LobbyRole role) {
        Player p = new Player();
        p.setId(id);
        p.setRoom(room);
        p.setRole(role);
        return p;
    }

    private void roster(Player... current) {
        when(rooms.findByRoomCodeForUpdate("AB12CD")).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(current));
    }

    @Test
    void readyCanBeSetIndependentlyOfRoleChoice() {
        roster(actor, guest, moderator);
        service.setReady("ab12cd", 12L, true);
        assertTrue(actor.isReady());
        assertEquals(LobbyRole.GUEST, actor.getRole());
        assertEquals(RoomStatus.WAITING, room.getStatus()); // R16 owns game start
        verify(rooms).findByRoomCodeForUpdate("AB12CD");
    }

    @Test
    void readyCanBeCancelledWhenOtherPlayersLeave() {
        actor.setReady(true);
        actor.setRole(LobbyRole.NONE);
        roster(actor);
        service.setReady("AB12CD", 12L, false);
        assertFalse(actor.isReady());
        assertEquals(LobbyRole.NONE, actor.getRole());
    }

    @Test
    void readyTrueRequiresRoleFirstEvenWhenOnlyOnePlayerIsPresent() {
        actor.setRole(LobbyRole.NONE);
        roster(actor);
        assertThrows(RoleRequiredException.class, () -> service.setReady("AB12CD", 12L, true));
        assertFalse(actor.isReady());
    }

    @Test
    void readyTrueRequiresThreePlayers() {
        roster(actor, guest);
        assertThrows(NotEnoughPlayersException.class, () -> service.setReady("AB12CD", 12L, true));
        assertFalse(actor.isReady());
    }

    @Test
    void playerWhoIsNotInRoomCannotSetReady() {
        roster(guest, moderator);
        assertThrows(PlayerNotInRoomException.class, () -> service.setReady("AB12CD", 12L, true));
        verifyNoInteractions(codes);
    }

    @Test
    void startedRoomCannotChangeReadyOrCancelIt() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(rooms.findByRoomCodeForUpdate("AB12CD")).thenReturn(Optional.of(room));
        actor.setReady(true);
        assertThrows(AlreadyStartedException.class, () -> service.setReady("AB12CD", 12L, false));
        assertTrue(actor.isReady());
        verify(players, never()).findByRoomForUpdate(any());
    }

    @Test
    void missingRoomReturnsRoomNotFound() {
        when(rooms.findByRoomCodeForUpdate("AB12CD")).thenReturn(Optional.empty());
        assertThrows(InvalidRoomCodeException.class, () -> service.setReady("AB12CD", 12L, true));
        verifyNoInteractions(players);
    }

    @Test
    void invalidInputIsRejectedWithoutDatabaseAccess() {
        assertThrows(InvalidRoomCodeException.class, () -> service.setReady("??", 12L, true));
        assertThrows(PlayerNotInRoomException.class, () -> service.setReady("AB12CD", 0L, true));
        assertThrows(PlayerNotInRoomException.class, () -> service.setReady("AB12CD", null, false));
        verifyNoInteractions(rooms, players);
    }

    @Test
    void readyToggleIsIdempotent() {
        roster(actor, guest, moderator);
        service.setReady("AB12CD", 12L, true);
        service.setReady("AB12CD", 12L, true);
        assertTrue(actor.isReady());
        service.setReady("AB12CD", 12L, false);
        assertFalse(actor.isReady());
    }

    @Test
    void roomLockIsAcquiredBeforeMemberLock() {
        roster(actor, guest, moderator);
        service.setReady("AB12CD", 12L, true);
        InOrder order = inOrder(rooms, players);
        order.verify(rooms).findByRoomCodeForUpdate("AB12CD");
        order.verify(players).findByRoomForUpdate(room);
    }

    @Test
    void existingReadyStatesOfOtherPlayersAreNotChanged() {
        guest.setReady(true);
        moderator.setReady(true);
        roster(actor, guest, moderator);
        service.setReady("AB12CD", 12L, true);
        assertTrue(actor.isReady());
        assertTrue(guest.isReady());
        assertTrue(moderator.isReady());
        assertEquals(RoomStatus.WAITING, room.getStatus()); // Automatic start: later stage
    }
}
