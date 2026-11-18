package com.debate.game.service;

import com.debate.game.dto.RoomSettingsUpdateRequest;
import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.InvalidRoomCodeException;
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

@ExtendWith(MockitoExtension.class)
class RoomServiceSettingsTest {
    @Mock RoomRepository rooms;
    @Mock PlayerRepository players;
    @Mock NicknameValidator nicknameValidator;
    @Mock RoomCodeGenerator roomCodeGenerator;
    RoomService service;
    Room room;
    Player host;
    Player guest;

    @BeforeEach
    void setup() {
        service = new RoomService(rooms, players, nicknameValidator, roomCodeGenerator);
        room = new Room(); room.setId(3L); room.setRoomCode("ABC123");
        host = new Player(); host.setId(11L); host.setRoom(room); host.setReady(true);
        guest = new Player(); guest.setId(12L); guest.setRoom(room); guest.setReady(true);
        room.setHostPlayer(host);
    }

    private RoomSettingsUpdateRequest input() {
        return new RoomSettingsUpdateRequest("11", 4, 150, 75, 480, 5);
    }

    @Test
    void hostUpdatesAllFiveValuesAndClearsReady() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(host, guest));
        service.updateSettings("abc123", 11L, input());
        assertEquals(4, room.getMaxCardCount());
        assertEquals(150, room.getSpeechSeconds());
        assertEquals(75, room.getRebuttalSeconds());
        assertEquals(8, room.getTurnTimeLimit());
        assertEquals(5, room.getMaxItems());
        assertFalse(host.isReady());
        assertFalse(guest.isReady());
        InOrder order = inOrder(rooms, players);
        order.verify(rooms).findByRoomCodeForUpdate("ABC123");
        order.verify(players).findByRoomForUpdate(room);
    }

    @Test
    void lobbySnapshotReadsAllFiveValues() {
        room.setMaxCardCount(4); room.setTurnTimeLimit(8);
        room.setMaxItems(5); room.setSpeechSeconds(150); room.setRebuttalSeconds(75);
        when(rooms.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        when(players.findByRoom(room)).thenReturn(List.of(host, guest));
        LobbySnapshotResponse.LobbySettings settings = service.getLobby("ABC123").settings();
        assertEquals(5, settings.maxItems());
        assertEquals(480, settings.itemSeconds());
        assertEquals(4, settings.cardsPerItem());
        assertEquals(150, settings.speechSeconds());
        assertEquals(75, settings.rebuttalSeconds());
    }

    @Test
    void nonHostCannotModifySettingsOrReady() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(NotHostException.class, () -> service.updateSettings("ABC123", 12L, input()));
        assertTrue(host.isReady()); assertTrue(guest.isReady());
        verify(players, never()).findByRoomForUpdate(any());
    }

    @Test
    void startedRoomRejectsSettingsUpdates() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(AlreadyStartedException.class, () -> service.updateSettings("ABC123", 11L, input()));
        verifyNoInteractions(players);
    }

    @Test
    void invalidSettingsRejectedBeforeDatabaseWrite() {
        assertThrows(IllegalArgumentException.class, () ->
                service.updateSettings("ABC123", 11L,
                        new RoomSettingsUpdateRequest("11", 3, 120, 60, 299, 3)));
        assertThrows(IllegalArgumentException.class, () ->
                service.updateSettings("ABC123", 11L,
                        new RoomSettingsUpdateRequest("11", 3, null, 60, 300, 3)));
        assertThrows(IllegalArgumentException.class, () ->
                service.updateSettings("ABC123", 11L,
                        new RoomSettingsUpdateRequest("11", 0, 120, 60, 300, 3)));
        verifyNoInteractions(rooms, players);
    }

    @Test
    void invalidRoomAndPlayerRejected() {
        assertThrows(InvalidRoomCodeException.class, () -> service.updateSettings("bad!", 11L, input()));
        assertThrows(PlayerNotInRoomException.class, () -> service.updateSettings("ABC123", 0L, input()));
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.empty());
        assertThrows(InvalidRoomCodeException.class, () -> service.updateSettings("ABC123", 11L, input()));
    }
}
