package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.exception.*;
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

/** R16 start preconditions only; R16 MUST NOT change room status or create cards. */
@ExtendWith(MockitoExtension.class)
class RoomServiceStartConditionsTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock NicknameValidator nicknames;
    @Mock RoomCodeGenerator codes;
    RoomService service;
    Room room;
    Player host, guest, moderator;

    private Player player(long id, LobbyRole role, boolean ready) {
        Player p = new Player();
        p.setId(id);
        p.setRoom(room);
        p.setRole(role);
        p.setReady(ready);
        return p;
    }

    @BeforeEach
    void setup() {
        service = new RoomService(rooms, players, nicknames, codes);
        room = new Room();
        room.setId(10L);
        room.setRoomCode("ABC123");
        host = player(1L, LobbyRole.GUEST, true);
        guest = player(2L, LobbyRole.GUEST, true);
        moderator = player(3L, LobbyRole.MODERATOR, true);
        room.setHostPlayer(host);
    }

    private void stub(List<Player> members) {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(members);
    }

    @Test
    void hostGuestMayValidateAndDoesNotMutateState() {
        stub(List.of(host, guest, moderator));
        service.validateStartConditions(" abc123 ", 1L);
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertTrue(host.isReady());
        assertTrue(guest.isReady());
        assertTrue(moderator.isReady());
        InOrder order = inOrder(rooms, players);
        order.verify(rooms).findByRoomCodeForUpdate("ABC123");
        order.verify(players).findByRoomForUpdate(room);
        verify(rooms, never()).save(any());
        verify(players, never()).save(any());
    }

    @Test
    void hostModeratorMayAlsoValidate() {
        host.setRole(LobbyRole.MODERATOR);
        moderator.setRole(LobbyRole.GUEST);
        stub(List.of(host, guest, moderator));
        assertDoesNotThrow(() -> service.validateStartConditions("ABC123", 1L));
    }

    @Test
    void twoMembersNotEnoughEvenWhenBothReady() {
        stub(List.of(host, moderator));
        assertThrows(NotEnoughPlayersException.class,
                () -> service.validateStartConditions("ABC123", 1L));
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void fourMembersRejectedEvenWhenRolesAndReadiesCorrect() {
        Player extra = player(4L, LobbyRole.NONE, true);
        stub(List.of(host, guest, moderator, extra));
        InvalidStartConditionsException ex = assertThrows(InvalidStartConditionsException.class,
                () -> service.validateStartConditions("ABC123", 1L));
        assertEquals("INVALID_PLAYER_COUNT", ex.getCode());
    }

    @Test
    void wrongRolesRejectedEvenIfAllReady() {
        moderator.setRole(LobbyRole.NONE);
        stub(List.of(host, guest, moderator));
        InvalidStartConditionsException ex = assertThrows(InvalidStartConditionsException.class,
                () -> service.validateStartConditions("ABC123", 1L));
        assertEquals("INVALID_ROLE_COMPOSITION", ex.getCode());
    }

    @Test
    void twoModeratorsRejectedEvenIfAllReady() {
        guest.setRole(LobbyRole.MODERATOR);
        stub(List.of(host, guest, moderator));
        InvalidStartConditionsException ex = assertThrows(InvalidStartConditionsException.class,
                () -> service.validateStartConditions("ABC123", 1L));
        assertEquals("INVALID_ROLE_COMPOSITION", ex.getCode());
    }

    @Test
    void oneUnreadyPlayerBlocksStart() {
        guest.setReady(false);
        stub(List.of(host, guest, moderator));
        InvalidStartConditionsException ex = assertThrows(InvalidStartConditionsException.class,
                () -> service.validateStartConditions("ABC123", 1L));
        assertEquals("NOT_ALL_READY", ex.getCode());
        assertFalse(guest.isReady());
    }

    @Test
    void moderatorWithoutHostPermissionIsRejected() {
        stub(List.of(host, guest, moderator));
        assertThrows(NotHostException.class,
                () -> service.validateStartConditions("ABC123", 3L));
        verify(rooms, never()).save(any());
    }

    @Test
    void foreignPlayerIdRejectedBeforeRoleAndReadyChecks() {
        stub(List.of(host, guest, moderator));
        assertThrows(PlayerNotInRoomException.class,
                () -> service.validateStartConditions("ABC123", 44L));
    }

    @Test
    void startedRoomRejectedWithoutMemberRead() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(AlreadyStartedException.class,
                () -> service.validateStartConditions("ABC123", 1L));
        verifyNoInteractions(players);
    }

    @Test
    void unknownRoomRejectedWithoutMemberRead() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.empty());
        assertThrows(InvalidRoomCodeException.class,
                () -> service.validateStartConditions("ABC123", 1L));
        verifyNoInteractions(players);
    }

    @Test
    void malformedRoomCodeRejectedWithoutLookup() {
        assertThrows(InvalidRoomCodeException.class,
                () -> service.validateStartConditions("123", 1L));
        verifyNoInteractions(rooms, players);
    }

    @Test
    void nullOrInvalidPlayerRejectedWithoutLookup() {
        assertThrows(PlayerNotInRoomException.class,
                () -> service.validateStartConditions("ABC123", -1L));
        assertThrows(PlayerNotInRoomException.class,
                () -> service.validateStartConditions("ABC123", null));
        verifyNoInteractions(rooms, players);
    }
}
